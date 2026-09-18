package com.example.diagnostics

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticEvent(
    val id: String,
    val timestamp: Long,
    val severity: String,
    val category: String,
    val message: String,
    val details: String,
    val screen: String,
    val action: String
)

data class AudioStageMetrics(
    val stage: String,
    val inputRms: Float,
    val outputRms: Float,
    val inputPeak: Float,
    val outputPeak: Float,
    val frames: Long,
    val eqEnabled: Boolean,
    val eqDemandDb: Float,
    val activePlugins: Int,
    val updatedAt: Long
)

data class DiagnosticSummary(
    val status: String,
    val headline: String,
    val checks: List<String>
)

object RuntimeDiagnostics {
    private const val PREFS = "temporary_runtime_diagnostics"
    private const val EVENTS_KEY = "events"
    private const val MAX_EVENTS = 160

    private val initialized = AtomicBoolean(false)
    private val eventsRef = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    private val audioStages = ConcurrentHashMap<String, AudioStageMetrics>()
    private val screenRef = AtomicReference("unknown")
    private val actionRef = AtomicReference("")
    private var appContext: Context? = null
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var eqFingerprint = ""

    val events: StateFlow<List<DiagnosticEvent>> = eventsRef.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        loadPersistedEvents()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("CRITICAL", "CRASH", "Uncaught exception on ${thread.name}", throwable.stackTraceToString())
            originalHandler?.uncaughtException(thread, throwable)
        }
        record("INFO", "MONITOR", "Temporary Runtime QA Monitor started",
            "Observing playback, crossfade, mixer, EQ, DSP and DJ FX.")
    }

    fun setScreen(route: String?) { screenRef.set(route ?: "unknown") }
    fun recordAction(action: String) { actionRef.set(action) }

    fun record(severity: String, category: String, message: String, details: String = "") {
        if (!initialized.get()) return
        val e = DiagnosticEvent(
            UUID.randomUUID().toString().take(8),
            System.currentTimeMillis(),
            severity,
            category,
            message,
            details,
            screenRef.get(),
            actionRef.get()
        )
        eventsRef.value = (listOf(e) + eventsRef.value).take(MAX_EVENTS)
        persistEvents()
    }

    fun recordException(t: Throwable, category: String, message: String) =
        record("ERROR", category, message, t.stackTraceToString())

    fun recordPlayerError(message: String, details: String) =
        record("ERROR", "PLAYBACK_ERROR", message, details)

    fun recordPlaybackState(state: String, playing: Boolean, buffering: Boolean) =
        record("INFO", "PLAYBACK", "Player state: ${state}",
            "playing=${playing}, buffering=${buffering}")

    fun recordCrossfadeStarted(from: String, to: String, expectedMs: Long) =
        record("INFO", "CROSSFADE", "Crossfade started",
            "from=${from}, to=${to}, expected=${expectedMs}ms")

    fun recordCrossfadeCompleted(from: String, to: String, expectedMs: Long, actualMs: Long) {
        val delta = kotlin.math.abs(actualMs - expectedMs)
        val severity = when {
            delta <= 350L -> "INFO"
            delta <= 900L -> "WARNING"
            else -> "ERROR"
        }
        record(severity, "CROSSFADE",
            if (severity == "INFO") "Crossfade timing looks correct" else "Crossfade timing differs from target",
            "from=${from}, to=${to}, expected=${expectedMs}ms, actual=${actualMs}ms, delta=${delta}ms")
    }

    fun recordCrossfadeFailure(details: String) =
        record("ERROR", "CROSSFADE", "Crossfade failed", details)

    fun recordMixer(x: Float, a: Float, b: Float) {
        val expectedA = kotlin.math.cos(x * (kotlin.math.PI / 2.0)).toFloat()
        val expectedB = kotlin.math.sin(x * (kotlin.math.PI / 2.0)).toFloat()
        val error = maxOf(kotlin.math.abs(expectedA - a), kotlin.math.abs(expectedB - b))
        if (error > 0.03f) {
            record("WARNING", "MIXER", "Crossfader gain curve mismatch",
                "x=${x}, expectedA=${expectedA}, actualA=${a}, expectedB=${expectedB}, actualB=${b}")
        }
    }

    fun recordEqState(enabled: Boolean, levels: FloatArray, preamp: Float, bass: Float, treble: Float, preset: String) {
        val fingerprint = enabled.toString() + levels.joinToString(",") + preamp + bass + treble + preset
        if (fingerprint == eqFingerprint) return
        eqFingerprint = fingerprint
        val demand = maxOf(levels.maxOfOrNull { kotlin.math.abs(it) } ?: 0f, preamp, bass, treble)
        record("INFO", "EQUALIZER",
            if (enabled) "Equalizer enabled and broadcast" else "Equalizer disabled",
            "preset=${preset}, demand=${"%.1f".format(Locale.US, demand)}dB")
    }

    fun updateAudioDspMetrics(stage: String, inputRms: Float, outputRms: Float,
        inputPeak: Float, outputPeak: Float, frames: Long, eqEnabled: Boolean,
        eqDemandDb: Float, activePlugins: Int) {
        audioStages[stage] = AudioStageMetrics(
            stage, inputRms, outputRms, inputPeak, outputPeak, frames,
            eqEnabled, eqDemandDb, activePlugins, System.currentTimeMillis()
        )
    }

    fun snapshotAudio(): List<AudioStageMetrics> = audioStages.values.sortedBy { it.stage }

    fun analyzeNow(): DiagnosticSummary {
        val errors = eventsRef.value.count { it.severity == "ERROR" || it.severity == "CRITICAL" }
        val warnings = eventsRef.value.count { it.severity == "WARNING" }
        val checks = mutableListOf<String>()
        val now = System.currentTimeMillis()

        snapshotAudio().forEach { s ->
            if (now - s.updatedAt < 5000L) {
                when {
                    s.outputPeak >= 0.995f ->
                        checks += "${s.stage}: peak قريب من 0 dBFS؛ افحص clipping."
                    s.inputRms > 0.01f && s.outputRms < 0.001f ->
                        checks += "${s.stage}: PCM موجود في الداخل لكن الخرج شبه صامت."
                    s.eqEnabled && s.eqDemandDb > 0.1f &&
                        kotlin.math.abs(s.outputRms - s.inputRms) < 0.0005f ->
                        checks += "${s.stage}: EQ مفعّل لكن التغيير المقاس ضعيف جدًا."
                    s.frames > 0L ->
                        checks += "${s.stage}: DSP يعالج PCM فعليًا."
                }
            }
        }
        if (eventsRef.value.any { it.category == "DJ_FX" && it.severity == "ERROR" })
            checks += "DJ FX: يوجد Sound/Pad فشل تحميله أو تشغيله."
        if (eventsRef.value.any { it.category == "CROSSFADE" && it.severity == "ERROR" })
            checks += "Crossfade: يوجد فشل مسجل."
        if (checks.isEmpty())
            checks += "لا توجد إشارة حمراء في آخر القياسات؛ شغّل الوظائف أثناء المراقبة."

        return DiagnosticSummary(
            when { errors > 0 -> "ERROR"; warnings > 0 -> "WARNING"; else -> "OK" },
            "Errors=${errors} • warnings=${warnings} • audio stages=${audioStages.size}",
            checks
        )
    }

    fun clear() {
        eventsRef.value = emptyList()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(EVENTS_KEY)?.apply()
    }

    fun exportReport(): File? {
        val ctx = appContext ?: return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(ctx.cacheDir, "runtime_diagnostics_${stamp}.json")
        val root = JSONObject()
            .put("appVersion", runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown"))
            .put("androidVersion", Build.VERSION.RELEASE ?: "unknown")
            .put("deviceModel", Build.MODEL ?: "unknown")

        val a = JSONArray()
        eventsRef.value.forEach { e ->
            a.put(JSONObject().put("id", e.id).put("timestamp", e.timestamp)
                .put("severity", e.severity).put("category", e.category)
                .put("message", e.message).put("details", e.details)
                .put("screen", e.screen).put("action", e.action))
        }
        root.put("events", a)

        val audio = JSONArray()
        snapshotAudio().forEach { s ->
            audio.put(JSONObject().put("stage", s.stage).put("inputRms", s.inputRms)
                .put("outputRms", s.outputRms).put("inputPeak", s.inputPeak)
                .put("outputPeak", s.outputPeak).put("frames", s.frames)
                .put("eqEnabled", s.eqEnabled).put("eqDemandDb", s.eqDemandDb)
                .put("activePlugins", s.activePlugins).put("updatedAt", s.updatedAt))
        }
        root.put("audioStages", audio)
        file.writeText(root.toString(2))
        return file
    }

    fun time(t: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t))

    private fun loadPersistedEvents() {
        val raw = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(EVENTS_KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            eventsRef.value = buildList {
                for (i in 0 until minOf(arr.length(), MAX_EVENTS)) {
                    val o = arr.getJSONObject(i)
                    add(DiagnosticEvent(o.optString("id"), o.optLong("timestamp"), o.optString("severity"),
                        o.optString("category"), o.optString("message"), o.optString("details"),
                        o.optString("screen"), o.optString("action")))
                }
            }
        }
    }

    private fun persistEvents() {
        val arr = JSONArray()
        eventsRef.value.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("timestamp", e.timestamp)
                .put("severity", e.severity).put("category", e.category)
                .put("message", e.message).put("details", e.details)
                .put("screen", e.screen).put("action", e.action))
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(EVENTS_KEY, arr.toString())?.apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemporaryDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val events by RuntimeDiagnostics.events.collectAsState()
    var summary by remember { mutableStateOf(RuntimeDiagnostics.analyzeNow()) }
    var selected by remember { mutableStateOf<DiagnosticEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Runtime QA Monitor")
                        Text("TEMPORARY • REMOVE AFTER FIXES",
                            style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineLarge) }
                },
                actions = {
                    IconButton(onClick = { summary = RuntimeDiagnostics.analyzeNow() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                    IconButton(onClick = {
                        val file = RuntimeDiagnostics.exportReport()
                        Toast.makeText(context, file?.name ?: "Export failed", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Filled.BugReport, null) }
                    IconButton(onClick = {
                        RuntimeDiagnostics.clear()
                        summary = RuntimeDiagnostics.analyzeNow()
                    }) { Icon(Icons.Filled.Delete, null) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SummaryCard(summary) }
            item { AudioCard(RuntimeDiagnostics.snapshotAudio()) }
            item {
                Text("LIVE EVENTS", style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items(events, key = { it.id }) { event ->
                EventCard(event) { selected = event }
            }
        }
    }

    selected?.let { e ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${e.category} • ${e.severity}") },
            text = {
                Column {
                    Text(e.message)
                    Spacer(Modifier.height(8.dp))
                    Text("Time: ${RuntimeDiagnostics.time(e.timestamp)}")
                    Text("Screen: ${e.screen}")
                    Text("Action: ${e.action}")
                    if (e.details.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(e.details)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SummaryCard(summary: DiagnosticSummary) {
    val icon = when (summary.status) {
        "ERROR" -> Icons.Filled.Error
        "WARNING" -> Icons.Filled.Warning
        else -> Icons.Filled.CheckCircle
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("MONITOR STATUS", style = MaterialTheme.typography.labelMedium)
                Text(summary.status, style = MaterialTheme.typography.titleLarge)
                Text(summary.headline, style = MaterialTheme.typography.bodySmall)
                summary.checks.take(6).forEach {
                    Text("• ${it}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AudioCard(stages: List<AudioStageMetrics>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("AUDIO PIPELINE", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (stages.isEmpty()) {
                Text("No PCM measurements yet. Start playback.", style = MaterialTheme.typography.bodySmall)
            } else {
                stages.forEach { s ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(s.stage, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "RMS ${"%.4f".format(s.inputRms)} → ${"%.4f".format(s.outputRms)}   " +
                                "Peak ${"%.3f".format(s.inputPeak)} → ${"%.3f".format(s.outputPeak)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "EQ=${if (s.eqEnabled) "ON" else "OFF"} • demand=${"%.1f".format(s.eqDemandDb)}dB • FX=${s.activePlugins}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: DiagnosticEvent, onClick: () -> Unit) {
    val icon = when (event.severity) {
        "ERROR", "CRITICAL" -> Icons.Filled.Error
        "WARNING" -> Icons.Filled.Warning
        else -> Icons.Filled.CheckCircle
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("${event.category} • ${event.severity}", style = MaterialTheme.typography.labelMedium)
                Text(event.message, style = MaterialTheme.typography.bodyMedium)
                Text("${RuntimeDiagnostics.time(event.timestamp)} • ${event.screen}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
