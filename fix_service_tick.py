import re

path = "app/src/main/java/com/example/player/MusicService.kt"
with open(path, 'r') as f:
    content = f.read()

# Add CoroutineScope and Job
if "private val serviceJob" not in content:
    imports = "import kotlinx.coroutines.*\n"
    content = content.replace("import android.appwidget.AppWidgetManager", "import android.appwidget.AppWidgetManager\n" + imports)
    
    props = """
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressJob: Job? = null
"""
    content = content.replace("private var micActive = false\n", "private var micActive = false\n" + props)

# Add tick methods
if "fun startProgressTick" not in content:
    methods = """
    private fun startProgressTick() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                if (playerController?.isPlaying == true) {
                    refreshPlaybackPosition()
                    playerController?.let {
                        PlaybackNotificationRouter.updateProgress(
                            applicationContext, "player", it.currentPositionMs, it.durationMs
                        )
                    }
                } else {
                    progressJob?.cancel()
                    break
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTick() {
        progressJob?.cancel()
        progressJob = null
    }

    fun refreshPlaybackPosition() {
        val playing = playerController?.isPlaying ?: false
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    playerController?.currentPositionMs ?: 0L,
                    if (playing) 1f else 0f
                ).build()
        )
    }
"""
    content = content.replace("fun updateNotification(", methods + "\n    fun updateNotification(")

# Hook into start/stop logic
if "startProgressTick()" not in content:
    # Modify updateNotification to start/stop
    def repl_un(m):
        return """
        if (isPlaying) startProgressTick() else stopProgressTick()
        """ + m.group(0)
    content = re.sub(r'mediaSession\.isActive = true', repl_un, content)

# Modify onDestroy to cancel scope
if "serviceJob.cancel()" not in content:
    content = content.replace("override fun onDestroy() {", "override fun onDestroy() {\n        serviceJob.cancel()")

with open(path, 'w') as f:
    f.write(content)
