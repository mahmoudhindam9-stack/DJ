package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

private data class SamplePad(val name: String, val url: String)

private data class SampleBank(val name: String, val pads: List<SamplePad>)

private val sampleBanks = listOf(
    SampleBank("Hard Trap", listOf(
        SamplePad("Kick 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-01.wav"),
        SamplePad("Kick 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-02.wav"),
        SamplePad("Kick 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-03.wav"),
        SamplePad("808 Dist", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/808s/808-bass-dist.wav"),
        SamplePad("808 Sub", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/808s/808-bass-sub.wav"),
        SamplePad("Snare 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-01.wav"),
        SamplePad("Snare 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-02.wav"),
        SamplePad("Snare 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-03.wav"),
        SamplePad("Clap 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/claps/clap-01.wav"),
        SamplePad("Clap 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/claps/cl.wav"),
        SamplePad("Closed HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/hi-hats/hi-hat-closed-01.wav"),
        SamplePad("Closed HH 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/hi-hats/ch.wav"),
        SamplePad("Open HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/open-hats/open-hat-01.wav"),
        SamplePad("Cowbell", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/percs/perc-cowbell.wav"),
        SamplePad("Rimshot", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/percs/perc-rimshot.wav"),
        SamplePad("Cymbal FX", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/fx/fx-cymbal.wav")
    )),
    SampleBank("Bounce", listOf(
        SamplePad("Kick 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-01.wav"),
        SamplePad("Kick 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-02.wav"),
        SamplePad("Kick 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-03.wav"),
        SamplePad("808 Long", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-bass-long.wav"),
        SamplePad("808 Punch", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-bass-punch.wav"),
        SamplePad("Snare 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-01.wav"),
        SamplePad("Snare 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-02.wav"),
        SamplePad("Snare 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-03.wav"),
        SamplePad("Clap 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/claps/clap-01.wav"),
        SamplePad("Clap 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/claps/cp.wav"),
        SamplePad("Closed HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/hi-hats/hi-hat-closed-01.wav"),
        SamplePad("Open HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/open-hats/open-hat-01.wav"),
        SamplePad("High Tom", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/percs/perc-high-tom.wav"),
        SamplePad("Low Tom", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/percs/perc-low-tom.wav"),
        SamplePad("808 Round", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-round-long.wav"),
        SamplePad("Bounce FX", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/fx/fx-cymbal.wav")
    )),
    SampleBank("Soulful Vintage", listOf(
        SamplePad("Kick 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/kicks/vintage-kick-01.wav"),
        SamplePad("Kick 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/kicks/vintage-kick-02.wav"),
        SamplePad("Kick 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/kicks/vintage-kick-03.wav"),
        SamplePad("808 Lofi", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/808s/808-bass-lofi.wav"),
        SamplePad("808 Texture", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/808s/808-lofi.wav"),
        SamplePad("Snare 1", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/snares/vintage-snare-01.wav"),
        SamplePad("Snare 2", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/snares/vintage-snare-02.wav"),
        SamplePad("Snare 3", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/snares/vintage-snare-03.wav"),
        SamplePad("Clap", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/claps/vintage-clap-01.wav"),
        SamplePad("Clap Lofi", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/claps/cl-lofi.wav"),
        SamplePad("Closed HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/hi-hats/ch-lofi.wav"),
        SamplePad("Open HH Lofi", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/open-hats/oh00-lofi.wav"),
        SamplePad("Open HH", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/open-hats/open-hat-01.wav"),
        SamplePad("Maraca", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/percs/perc-maraca.wav"),
        SamplePad("Lofi Tom", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/percs/ht00-lofi.wav"),
        SamplePad("Vintage FX", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/03-soulful-vintage/fx/cy0000-lofi.wav")
    ))
)

private class ProfessionalSampleEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val players = mutableMapOf<String, MediaPlayer>()
    private val cacheDir = File(context.cacheDir, "dj_sampler_cc0").apply { mkdirs() }

    fun play(pad: SamplePad, onState: (Boolean, String) -> Unit) {
        scope.launch {
            onState(true, "Loading ${pad.name}…")
            try {
                val file = cachedFile(pad)
                withContext(Dispatchers.Main) {
                    players[pad.name]?.release()
                    val player = MediaPlayer()
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    player.setDataSource(file.absolutePath)
                    player.setOnPreparedListener {
                        it.start()
                        onState(false, "Playing ${pad.name}")
                    }
                    player.setOnCompletionListener {
                        it.release()
                        players.remove(pad.name)
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        mp.release()
                        players.remove(pad.name)
                        onState(false, "Could not play ${pad.name}")
                        true
                    }
                    players[pad.name] = player
                    player.prepareAsync()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onState(false, "Sample unavailable: ${pad.name}") }
            }
        }
    }

    private fun cachedFile(pad: SamplePad): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(pad.url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) } + ".wav"
        val file = File(cacheDir, name)
        if (!file.exists() || file.length() < 128) {
            URL(pad.url).openStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        }
        return file
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
        scope.coroutineContext[Job]?.cancel()
    }
}

@Composable
fun ProfessionalSamplerBoard() {
    var bankIndex by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("CC0 sample bank ready") }
    var loading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { ProfessionalSampleEngine(context) }
    val bank = sampleBanks[bankIndex]

    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("PRO SAMPLER", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Real WAV one-shots • CC0 • 3 banks × 16 pads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (loading) "LOADING" else "READY", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sampleBanks.forEachIndexed { index, item ->
                    FilterChip(selected = bankIndex == index, onClick = { bankIndex = index }, label = { Text(item.name, fontSize = 10.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(bank.pads) { pad ->
                    Button(
                        onClick = {
                            engine.play(pad) { isLoading, newStatus ->
                                loading = isLoading
                                status = newStatus
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) { Text(pad.name, fontSize = 10.sp, maxLines = 2) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Source: Boochi44/free-drum-samples — CC0 1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
