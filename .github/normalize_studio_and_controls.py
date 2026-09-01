from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
SERVICE = ROOT / "app/src/main/java/com/example/player/MusicService.kt"
PLAYER = ROOT / "app/src/main/java/com/example/player/AudioPlayerController.kt"
EQ = ROOT / "app/src/main/java/com/example/player/EqualizerController.kt"
WIDGET = ROOT / "app/src/main/java/com/example/widget/MusicWidgetProvider.kt"
WIDGET_XML = ROOT / "app/src/main/res/layout/music_widget.xml"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


def patch_main(text: str) -> str:
    text = replace_once(text, 'import com.example.org.OrgScreen\n', 'import com.example.studio.MusicStudioController\nimport com.example.studio.MusicStudioScreen\n', 'studio import')
    text = replace_once(text, '    val micController = remember { MicController(context) }\n', '    val micController = remember { MicController(context) }\n    val musicStudioController = remember { MusicStudioController(context) }\n', 'studio controller')
    text = replace_once(text, '            eqController.release()\n', '            eqController.release()\n            musicStudioController.close()\n', 'studio dispose')
    org_nav = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "ORG") },\n                    label = { Text("ORG") },\n                    selected = currentDestination?.route == "org",\n                    onClick = {\n                        navController.navigate("org") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )'''
    studio_nav = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "Studio") },\n                    label = { Text("Studio") },\n                    selected = currentDestination?.route == "studio",\n                    onClick = {\n                        navController.navigate("studio") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )'''
    text = replace_once(text, org_nav, studio_nav, 'studio navigation')
    text = replace_once(text, '            composable("org") {\n                OrgScreen()\n            }\n', '            composable("studio") {\n                MusicStudioScreen(musicStudioController)\n            }\n', 'studio route')
    return text


def patch_player(text: str) -> str:
    old = '''                .putString(KEY_REPEAT, repeatOption.name)\n                .putFloat(KEY_VOLUME, volume)\n                .apply()'''
    new = '''                .putString(KEY_REPEAT, repeatOption.name)\n                .putFloat(KEY_VOLUME, volume)\n                .putString(KEY_TITLE, currentSong?.title ?: "مشغل الموسيقى")\n                .putString(KEY_ARTIST, currentSong?.artist ?: "موسيقى")\n                .apply()'''
    text = replace_once(text, old, new, 'player session metadata')
    old2 = '''        private const val KEY_REPEAT = "repeat"\n        private const val KEY_VOLUME = "volume"'''
    new2 = '''        private const val KEY_REPEAT = "repeat"\n        private const val KEY_VOLUME = "volume"\n        const val KEY_TITLE = "title"\n        const val KEY_ARTIST = "artist"'''
    text = replace_once(text, old2, new2, 'player metadata keys')
    return text


def patch_eq(text: str) -> str:
    text = replace_once(text, 'class EqualizerController {\n    private var equalizer: Equalizer? = null\n', 'class EqualizerController {\n    init { activeInstance = this; loadQuickState() }\n\n    private var equalizer: Equalizer? = null\n', 'eq active instance')
    text = replace_once(text, '        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = safe)\n        selectedPreset = "Custom"\n        applyAllToHardware()\n', '        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = safe)\n        selectedPreset = "Custom"\n        persistQuickState()\n        applyAllToHardware()\n', 'eq persistence')
    text = replace_once(text, '    fun release() {\n        try { equalizer?.release() } catch (_: Throwable) { }\n', '    fun release() {\n        persistQuickState()\n        if (activeInstance === this) activeInstance = null\n        try { equalizer?.release() } catch (_: Throwable) { }\n', 'eq release persistence')
    marker = '\n    companion object {\n'
    companion = '''\n    private fun persistQuickState() {\n        try {\n            val prefs = activePrefs()\n            prefs.edit()\n                .putInt("bass", bands[0].currentLevelDb)\n                .putInt("mid", bands[4].currentLevelDb)\n                .putInt("treble", bands[9].currentLevelDb)\n                .apply()\n        } catch (_: Throwable) { }\n    }\n\n    private fun loadQuickState() {\n        try {\n            val prefs = activePrefs()\n            bands[0] = bands[0].copy(currentLevelDb = prefs.getInt("bass", 0).coerceIn(-6, 6))\n            bands[4] = bands[4].copy(currentLevelDb = prefs.getInt("mid", 0).coerceIn(-6, 6))\n            bands[9] = bands[9].copy(currentLevelDb = prefs.getInt("treble", 0).coerceIn(-6, 6))\n        } catch (_: Throwable) { }\n    }\n\n    private fun activePrefs(): android.content.SharedPreferences =\n        (activeContext ?: throw IllegalStateException("Equalizer context unavailable")).getSharedPreferences("quick_eq", android.content.Context.MODE_PRIVATE)\n\n    companion object {\n        @Volatile var activeInstance: EqualizerController? = null\n        @Volatile var activeContext: android.content.Context? = null\n\n        fun bindContext(context: android.content.Context) { activeContext = context.applicationContext }\n\n        fun adjustQuickBand(band: Int) {\n            val instance = activeInstance\n            if (instance != null) {\n                val index = when (band) { 0 -> 0; 1 -> 4; else -> 9 }\n                val current = instance.bands[index].currentLevelDb\n                instance.updateBandLevel(index, if (current >= 6) -6 else current + 1)\n            } else {\n                val context = activeContext ?: return\n                val prefs = context.getSharedPreferences("quick_eq", android.content.Context.MODE_PRIVATE)\n                val key = when (band) { 0 -> "bass"; 1 -> "mid"; else -> "treble" }\n                val current = prefs.getInt(key, 0)\n                prefs.edit().putInt(key, if (current >= 6) -6 else current + 1).apply()\n            }\n        }\n    }\n'''
    text = replace_once(text, marker, companion, 'eq companion block')
    return text


def patch_service(text: str) -> str:
    text = replace_once(text, '        const val ACTION_STOP = "com.example.action.STOP"\n', '        const val ACTION_STOP = "com.example.action.STOP"\n        const val ACTION_EQ_BASS = "com.example.action.EQ_BASS"\n        const val ACTION_EQ_MID = "com.example.action.EQ_MID"\n        const val ACTION_EQ_TREBLE = "com.example.action.EQ_TREBLE"\n', 'service eq actions')
    text = replace_once(text, '        when (intent?.action) {\n', '        when (intent?.action) {\n            ACTION_EQ_BASS -> { EqualizerController.adjustQuickBand(0); refreshWidgetFromStoredState() }\n            ACTION_EQ_MID -> { EqualizerController.adjustQuickBand(1); refreshWidgetFromStoredState() }\n            ACTION_EQ_TREBLE -> { EqualizerController.adjustQuickBand(2); refreshWidgetFromStoredState() }\n', 'service eq handling')
    text = replace_once(text, '        startForeground(NOTIFICATION_ID, notification)\n        updateWidget(title, artist, isPlaying)\n', '        startForeground(NOTIFICATION_ID, notification)\n        updateWidget(title, artist, isPlaying)\n', 'service notification anchor')
    refresh = '''\n    private fun refreshWidgetFromStoredState() {\n        val prefs = getSharedPreferences("dj_player_session", MODE_PRIVATE)\n        val title = prefs.getString("title", "مشغل الموسيقى") ?: "مشغل الموسيقى"\n        val artist = prefs.getString("artist", "موسيقى") ?: "موسيقى"\n        val playing = prefs.getBoolean("playing", false)\n        updateWidget(title, artist, playing)\n    }\n'''
    text = replace_once(text, '    private fun createNotificationChannel() {\n', refresh + '\n    private fun createNotificationChannel() {\n', 'widget refresh helper')
    return text


def patch_widget(text: str) -> str:
    text = '''package com.example.widget\n\nimport android.app.PendingIntent\nimport android.appwidget.AppWidgetManager\nimport android.appwidget.AppWidgetProvider\nimport android.content.Context\nimport android.content.Intent\nimport android.widget.RemoteViews\nimport com.example.R\nimport com.example.player.AudioPlayerController\nimport com.example.player.EqualizerController\nimport com.example.player.MusicService\n\nclass MusicWidgetProvider : AppWidgetProvider() {\n    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {\n        val prefs = context.getSharedPreferences("dj_player_session", Context.MODE_PRIVATE)\n        val title = prefs.getString(AudioPlayerController.KEY_TITLE, "مشغل الموسيقى") ?: "مشغل الموسيقى"\n        val artist = prefs.getString(AudioPlayerController.KEY_ARTIST, "موسيقى") ?: "موسيقى"\n        val playing = prefs.getBoolean("playing", false)\n        val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)\n        appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id, title, artist, playing, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0)) }\n    }\n\n    companion object {\n        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String, isPlaying: Boolean) {\n            val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)\n            updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0))\n        }\n\n        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String, isPlaying: Boolean, bass: Int, mid: Int, treble: Int) {\n            val views = RemoteViews(context.packageName, R.layout.music_widget)\n            views.setTextViewText(R.id.widget_title, title)\n            views.setTextViewText(R.id.widget_artist, if (isPlaying) "▶ Playing" else "⏸ Paused")\n            views.setImageViewResource(R.id.widget_btn_play, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)\n            views.setProgressBar(R.id.widget_eq_bass, 12, (bass + 6).coerceIn(0, 12), false)\n            views.setProgressBar(R.id.widget_eq_mid, 12, (mid + 6).coerceIn(0, 12), false)\n            views.setProgressBar(R.id.widget_eq_treble, 12, (treble + 6).coerceIn(0, 12), false)\n\n            val prevPending = PendingIntent.getService(context, appWidgetId * 10, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_PREV), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            val playPending = PendingIntent.getService(context, appWidgetId * 10 + 1, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            val nextPending = PendingIntent.getService(context, appWidgetId * 10 + 2, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            val bassPending = PendingIntent.getService(context, appWidgetId * 10 + 3, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_BASS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            val midPending = PendingIntent.getService(context, appWidgetId * 10 + 4, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_MID), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            val treblePending = PendingIntent.getService(context, appWidgetId * 10 + 5, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_TREBLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)\n            views.setOnClickPendingIntent(R.id.widget_btn_play, playPending)\n            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)\n            views.setOnClickPendingIntent(R.id.widget_eq_bass, bassPending)\n            views.setOnClickPendingIntent(R.id.widget_eq_mid, midPending)\n            views.setOnClickPendingIntent(R.id.widget_eq_treble, treblePending)\n            appWidgetManager.updateAppWidget(appWidgetId, views)\n        }\n    }\n}\n'''
    return text


def patch_widget_xml(text: str) -> str:
    return '''<?xml version="1.0" encoding="utf-8"?>\n<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    android:background="@drawable/widget_bg"\n    android:orientation="vertical"\n    android:padding="12dp">\n\n    <TextView android:id="@+id/widget_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="مشغل الموسيقى" android:textSize="14sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end" android:textColor="@android:color/white" />\n    <TextView android:id="@+id/widget_artist" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="⏸ Paused" android:textSize="11sp" android:maxLines="1" android:ellipsize="end" android:textColor="#AAAAAA" />\n\n    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:gravity="center" android:orientation="horizontal">\n        <ImageButton android:id="@+id/widget_btn_prev" android:layout_width="36dp" android:layout_height="36dp" android:background="@android:drawable/list_selector_background" android:src="@android:drawable/ic_media_previous" android:contentDescription="Previous" />\n        <ImageButton android:id="@+id/widget_btn_play" android:layout_width="44dp" android:layout_height="44dp" android:layout_marginStart="12dp" android:layout_marginEnd="12dp" android:background="@android:drawable/list_selector_background" android:src="@android:drawable/ic_media_play" android:contentDescription="Play/Pause" />\n        <ImageButton android:id="@+id/widget_btn_next" android:layout_width="36dp" android:layout_height="36dp" android:background="@android:drawable/list_selector_background" android:src="@android:drawable/ic_media_next" android:contentDescription="Next" />\n    </LinearLayout>\n\n    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:orientation="horizontal" android:gravity="center">\n        <ProgressBar android:id="@+id/widget_eq_bass" style="?android:attr/progressBarStyleHorizontal" android:layout_width="0dp" android:layout_height="8dp" android:layout_weight="1" android:max="12" android:progress="6" android:clickable="true" android:focusable="true" android:contentDescription="Bass EQ" />\n        <ProgressBar android:id="@+id/widget_eq_mid" style="?android:attr/progressBarStyleHorizontal" android:layout_width="0dp" android:layout_height="8dp" android:layout_weight="1" android:layout_marginStart="6dp" android:layout_marginEnd="6dp" android:max="12" android:progress="6" android:clickable="true" android:focusable="true" android:contentDescription="Mid EQ" />\n        <ProgressBar android:id="@+id/widget_eq_treble" style="?android:attr/progressBarStyleHorizontal" android:layout_width="0dp" android:layout_height="8dp" android:layout_weight="1" android:max="12" android:progress="6" android:clickable="true" android:focusable="true" android:contentDescription="Treble EQ" />\n    </LinearLayout>\n</LinearLayout>\n'''


def patch_manifest(text: str) -> str:
    text = replace_once(text, '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA" />\n', '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n', 'media foreground permission')
    return text


def main():
    main_text = patch_main(MAIN.read_text(encoding="utf-8"))
    player_text = patch_player(PLAYER.read_text(encoding="utf-8"))
    eq_text = patch_eq(EQ.read_text(encoding="utf-8"))
    service_text = patch_service(SERVICE.read_text(encoding="utf-8"))
    widget_text = patch_widget(WIDGET.read_text(encoding="utf-8"))
    widget_xml_text = patch_widget_xml(WIDGET_XML.read_text(encoding="utf-8"))
    manifest_text = patch_manifest(MANIFEST.read_text(encoding="utf-8"))
    MAIN.write_text(main_text, encoding="utf-8")
    PLAYER.write_text(player_text, encoding="utf-8")
    EQ.write_text(eq_text, encoding="utf-8")
    SERVICE.write_text(service_text, encoding="utf-8")
    WIDGET.write_text(widget_text, encoding="utf-8")
    WIDGET_XML.write_text(widget_xml_text, encoding="utf-8")
    MANIFEST.write_text(manifest_text, encoding="utf-8")
    print("Studio + notification + widget + quick EQ sources normalized")

if __name__ == "__main__":
    main()
