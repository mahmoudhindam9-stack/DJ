package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AudioItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class MelodyNote(val name: String, val frequencyHz: Double, val durationMs: Int = 260)

@Composable
fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>) {
    val sequence = remember { mutableStateListOf<MelodyNote>() }
    val notes = remember {
        listOf(
            MelodyNote("C4", 261.63), MelodyNote("D4", 293.66), MelodyNote("E4", 329.63),
            MelodyNote("F4", 349.23), MelodyNote("G4", 392.00), MelodyNote("A4", 440.00),
            MelodyNote("B4", 493.88), MelodyNote("C5", 523.25), MelodyNote("D5", 587.33),
            MelodyNote("E5", 659.25), MelodyNote("F5", 698.46), MelodyNote("G5", 783.99)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MELODY STUDIO", style = MaterialTheme.typography.titleMedium)
            Text(
                "ألّف لحن بالنقر على النغمات ثم شغّله أو احفظه كأغنية جديدة داخل المكتبة.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(notes) { note ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (sequence.size < 32) sequence.add(note)
                        },
                        label = { Text(note.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (sequence.isEmpty()) "اللحن فارغ" else "اللحن: ${sequence.joinToString(" – ") { it.name }}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (sequence.isNotEmpty()) MelodyAudio.play(sequence)
                }) { Text("تشغيل") }
                Button(onClick = { if (sequence.isNotEmpty()) MelodyAudio.play(sequence, loop = true) }) { Text("عزف متكرر") }
                Button(onClick = { sequence.clear() }) { Text("مسح") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (sequence.isNotEmpty()) {
                        val item = MelodyAudio.export(audioLibrary, sequence)
                        audioLibrary.add(item)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ اللحن في مكتبة الأغاني")
            }
        }
    }
}

private object MelodyAudio {
    private const val sampleRate = 44_100

    fun play(sequence: List<MelodyNote>, loop: Boolean = false) {
        Thread {
            try {
                val pcm = buildPcm(sequence)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                if (loop) {
                    while (!Thread.currentThread().isInterrupted) {
                        Thread.sleep(sequence.sumOf { it.durationMs }.toLong())
                    }
                } else {
                    Thread.sleep(sequence.sumOf { it.durationMs }.toLong() + 80L)
                }
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }.start()
    }

    fun export(audioLibrary: List<AudioItem>, sequence: List<MelodyNote>): AudioItem {
        val pcm = buildPcm(sequence)
        val fileName = "melody_${System.currentTimeMillis()}.wav"
        val file = File(MyApplicationGlobals.context.filesDir, fileName)
        FileOutputStream(file).use { out ->
            writeWavHeader(out, pcm.size)
            val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { bytes.putShort(it) }
            out.write(bytes.array())
        }
        return AudioItem(
            id = UUID.randomUUID().toString(),
            title = "Melody ${audioLibrary.count { it.title.startsWith("Melody ") } + 1}",
            artist = "My Melody",
            album = "Composed",
            durationMs = sequence.sumOf { it.durationMs }.toLong(),
            uri = android.net.Uri.fromFile(file),
            sizeBytes = file.length()
        )
    }

    private fun buildPcm(sequence: List<MelodyNote>): ShortArray {
        val totalSamples = sequence.sumOf { sampleRate * it.durationMs / 1000 }
        val pcm = ShortArray(totalSamples)
        var offset = 0
        sequence.forEach { note ->
            val count = sampleRate * note.durationMs / 1000
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val p = i.toDouble() / count
                val attack = (p / 0.08).coerceAtMost(1.0)
                val release = ((1.0 - p) / 0.18).coerceAtMost(1.0)
                val env = attack * release
                val fundamental = sin(2.0 * PI * note.frequencyHz * t)
                val harmonic = sin(2.0 * PI * note.frequencyHz * 2.0 * t) * 0.22
                pcm[offset + i] = ((fundamental + harmonic) * 0.48 * env * Short.MAX_VALUE)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            offset += count
        }
        return pcm
    }

    private fun writeWavHeader(out: FileOutputStream, sampleCount: Int) {
        val dataSize = sampleCount * 2
        val byteRate = sampleRate * 2
        fun le16(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
        fun le32(v: Int) = byteArrayOf(
            (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
        )
        out.write("RIFF".toByteArray())
        out.write(le32(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(1))
        out.write(le16(1))
        out.write(le32(sampleRate))
        out.write(le32(byteRate))
        out.write(le16(2))
        out.write(le16(16))
        out.write("data".toByteArray())
        out.write(le32(dataSize))
    }
}
