from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MIC = ROOT / 'app/src/main/java/com/example/player/MicController.kt'
MARKER = '// MIC_RECORDING_FORMAT_V1'


def patch_main():
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        return
    text = text.replace('    var outputExpanded by remember { mutableStateOf(false) }\n', '    var outputExpanded by remember { mutableStateOf(false) }\n    var recordingFormat by remember { mutableStateOf("WAV") }\n', 1)
    text = text.replace('ActivityResultContracts.CreateDocument("audio/wav")', 'ActivityResultContracts.CreateDocument("audio/*")', 1)
    text = text.replace('micController.savePendingRecording(uri)', 'micController.savePendingRecording(uri, recordingFormat)', 1)
    text = text.replace('micController.suggestedRecordingName())', 'micController.suggestedRecordingName(recordingFormat))', 1)
    anchor = '                Spacer(Modifier.height(10.dp))\n                Button(\n                    enabled = micController.isMicEnabled || micController.isOutputRecording,'
    if anchor not in text:
        raise SystemExit('Recording button anchor not found')
    format_ui = '''                Text("Format", style = MaterialTheme.typography.labelSmall)\n                Spacer(Modifier.height(4.dp))\n                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    FilterChip(selected = recordingFormat == "WAV", onClick = { recordingFormat = "WAV" }, label = { Text("WAV") })\n                    FilterChip(selected = recordingFormat == "MP3", onClick = { recordingFormat = "MP3" }, label = { Text("MP3") })\n                }\n                Spacer(Modifier.height(10.dp))\n'''
    text = text.replace(anchor, format_ui + anchor, 1)
    MAIN.write_text(text.replace('fun MicScreen(', MARKER + '\nfun MicScreen(', 1), encoding='utf-8')


def patch_mic():
    text = MIC.read_text(encoding='utf-8')
    if MARKER not in text:
        text = text.replace('import android.media.AudioTrack\n', 'import android.media.AudioTrack\nimport android.media.MediaCodec\nimport android.media.MediaFormat\n', 1)
        text = text.replace('import java.io.RandomAccessFile\n', 'import java.io.RandomAccessFile\nimport java.io.FileInputStream\nimport java.io.FileOutputStream\n', 1)
    if 'suspend fun savePendingRecording(uri: Uri, format: String)' in text:
        return
    old = '''    fun suggestedRecordingName(): String = "DJ_Mic_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.wav"\n\n    suspend fun savePendingRecording(uri: Uri): Boolean {\n        val source = pendingRecordingFile ?: return false\n        return try { context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return false; source.delete(); pendingRecordingFile = null; recordingStatus = "Recording saved successfully"; true }\n        catch (t: Throwable) { recordingStatus = "Save failed: ${t.message ?: "Unknown error"}"; false }\n    }\n'''
    if old not in text:
        raise SystemExit('Existing recording save block not found')
    new = '''    fun suggestedRecordingName(format: String = "WAV"): String {\n        val ext = if (format.equals("MP3", ignoreCase = true)) "mp3" else "wav"\n        return "DJ_Mic_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.$ext"\n    }\n\n    suspend fun savePendingRecording(uri: Uri, format: String = "WAV"): Boolean {\n        val source = pendingRecordingFile ?: return false\n        return try {\n            val sourceToSave = if (format.equals("MP3", ignoreCase = true)) {\n                val mp3 = File.createTempFile("dj_mic_", ".mp3", context.cacheDir)\n                if (!encodeWavToMp3(source, mp3)) { mp3.delete(); recordingStatus = "MP3 encoding is not available on this device"; return false }\n                mp3\n            } else source\n            context.contentResolver.openOutputStream(uri)?.use { out -> sourceToSave.inputStream().use { it.copyTo(out) } } ?: return false\n            if (sourceToSave != source) sourceToSave.delete()\n            source.delete(); pendingRecordingFile = null; recordingStatus = "Recording saved successfully"; true\n        } catch (t: Throwable) { recordingStatus = "Save failed: ${t.message ?: "Unknown error"}"; false }\n    }\n\n    private fun encodeWavToMp3(source: File, target: File): Boolean {\n        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false\n        val encoder = try { MediaCodec.createEncoderByType("audio/mpeg") } catch (_: Throwable) { return false }\n        var inputStream: FileInputStream? = null\n        var outputStream: FileOutputStream? = null\n        return try {\n            val format = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, 1)\n            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)\n            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)\n            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)\n            encoder.start()\n            inputStream = FileInputStream(source).also { it.skip(44) }\n            outputStream = FileOutputStream(target)\n            val info = MediaCodec.BufferInfo()\n            val pcm = ByteArray(bufferSize * 2)\n            var inputDone = false\n            var outputDone = false\n            while (!outputDone) {\n                if (!inputDone) {\n                    val inIndex = encoder.dequeueInputBuffer(10000)\n                    if (inIndex >= 0) {\n                        val inBuffer = encoder.getInputBuffer(inIndex) ?: return false\n                        inBuffer.clear()\n                        val read = inputStream.read(pcm)\n                        if (read < 0) {\n                            encoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)\n                            inputDone = true\n                        } else {\n                            inBuffer.put(pcm, 0, read)\n                            val pts = recordedPcmBytes.coerceAtLeast(read.toLong()) * 1000000L / (sampleRate * 2L)\n                            encoder.queueInputBuffer(inIndex, 0, read, pts, 0)\n                        }\n                    }\n                }\n                when (val outIndex = encoder.dequeueOutputBuffer(info, 10000)) {\n                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit\n                    else -> if (outIndex >= 0) {\n                        val outBuffer = encoder.getOutputBuffer(outIndex)\n                        if (outBuffer != null && info.size > 0) {\n                            outBuffer.position(info.offset); outBuffer.limit(info.offset + info.size);\n                            val bytes = ByteArray(info.size); outBuffer.get(bytes); outputStream.write(bytes)\n                        }\n                        encoder.releaseOutputBuffer(outIndex, false)\n                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true\n                    }\n                }\n            }\n            true\n        } catch (_: Throwable) { false }\n        finally {\n            try { inputStream?.close() } catch (_: Throwable) { }\n            try { outputStream?.close() } catch (_: Throwable) { }\n            try { encoder.stop() } catch (_: Throwable) { }\n            try { encoder.release() } catch (_: Throwable) { }\n        }\n    }\n'''
    MIC.write_text(text.replace(old, new, 1), encoding='utf-8')

patch_main(); patch_mic(); print('Microphone recording WAV/MP3 format selection applied')
