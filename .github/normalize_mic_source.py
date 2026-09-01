from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"


def remove_duplicate_toggle_functions(text: str) -> str:
    pattern = re.compile(
        r'\n    fun toggleVoiceProcessing\(enabled: Boolean\) \{.*?\n    \}\n',
        re.S,
    )
    body = '''\n    fun toggleVoiceProcessing(enabled: Boolean) {\n        voiceProcessingEnabled = enabled\n        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }\n        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }\n        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"\n    }\n'''
    text = pattern.sub("\n", text)
    anchor = '    private fun startMicForegroundService() {'
    if body.strip() not in text:
        if anchor not in text:
            raise SystemExit("startMicForegroundService anchor not found")
        text = text.replace(anchor, body + "\n" + anchor, 1)
    return text


def normalize_recording_api(text: str) -> str:
    block = re.compile(
        r'\n    fun suggestedRecordingName\(\).*?\n    fun discardPendingRecording\(\)',
        re.S,
    )
    replacement = '''\n    fun suggestedRecordingName(format: String = "WAV"): String {\n        val ext = if (format.equals("MP3", ignoreCase = true)) "mp3" else "wav"\n        return "DJ_Mic_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.$ext"\n    }\n\n    suspend fun savePendingRecording(uri: Uri, format: String = "WAV"): Boolean {\n        val source = pendingRecordingFile ?: return false\n        return try {\n            val sourceToSave = if (format.equals("MP3", ignoreCase = true)) {\n                val mp3 = File.createTempFile("dj_mic_", ".mp3", context.cacheDir)\n                if (!encodeWavToMp3(source, mp3)) {\n                    mp3.delete()\n                    recordingStatus = "MP3 encoding is not available on this device"\n                    return false\n                }\n                mp3\n            } else source\n\n            context.contentResolver.openOutputStream(uri)?.use { out ->\n                sourceToSave.inputStream().use { it.copyTo(out) }\n            } ?: return false\n\n            if (sourceToSave != source) sourceToSave.delete()\n            source.delete()\n            pendingRecordingFile = null\n            recordingStatus = "Recording saved successfully"\n            true\n        } catch (t: Throwable) {\n            recordingStatus = "Save failed: ${t.message ?: "Unknown error"}"\n            false\n        }\n    }\n\n    private fun encodeWavToMp3(source: File, target: File): Boolean {\n        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false\n        val encoder = try { MediaCodec.createEncoderByType("audio/mpeg") } catch (_: Throwable) { return false }\n        var inputStream: FileInputStream? = null\n        var outputStream: FileOutputStream? = null\n        return try {\n            val format = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, 1)\n            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)\n            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)\n            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)\n            encoder.start()\n            inputStream = FileInputStream(source).also { it.skip(44) }\n            outputStream = FileOutputStream(target)\n            val info = MediaCodec.BufferInfo()\n            val pcm = ByteArray(bufferSize * 2)\n            var inputDone = false\n            var outputDone = false\n            var bytesSubmitted = 0L\n            while (!outputDone) {\n                if (!inputDone) {\n                    val inIndex = encoder.dequeueInputBuffer(10000)\n                    if (inIndex >= 0) {\n                        val inBuffer = encoder.getInputBuffer(inIndex) ?: return false\n                        inBuffer.clear()\n                        val read = inputStream.read(pcm)\n                        if (read < 0) {\n                            encoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)\n                            inputDone = true\n                        } else if (read > 0) {\n                            inBuffer.put(pcm, 0, read)\n                            val ptsUs = bytesSubmitted * 1000000L / (sampleRate * 2L)\n                            encoder.queueInputBuffer(inIndex, 0, read, ptsUs, 0)\n                            bytesSubmitted += read.toLong()\n                        }\n                    }\n                }\n\n                when (val outIndex = encoder.dequeueOutputBuffer(info, 10000)) {\n                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit\n                    else -> if (outIndex >= 0) {\n                        val outBuffer = encoder.getOutputBuffer(outIndex)\n                        if (outBuffer != null && info.size > 0) {\n                            outBuffer.position(info.offset)\n                            outBuffer.limit(info.offset + info.size)\n                            val bytes = ByteArray(info.size)\n                            outBuffer.get(bytes)\n                            outputStream.write(bytes)\n                        }\n                        encoder.releaseOutputBuffer(outIndex, false)\n                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true\n                    }\n                }\n            }\n            true\n        } catch (_: Throwable) {\n            false\n        } finally {\n            try { inputStream?.close() } catch (_: Throwable) { }\n            try { outputStream?.close() } catch (_: Throwable) { }\n            try { encoder.stop() } catch (_: Throwable) { }\n            try { encoder.release() } catch (_: Throwable) { }\n        }\n    }\n\n    fun discardPendingRecording()'''
    if not block.search(text):
        raise SystemExit("recording API block not found")
    text = block.sub(replacement, text, count=1)
    return text


def normalize_stop_mic(text: str) -> str:
    # Collapse accidental repeated cleanup pairs to one call each.
    pair = ('        if (isOutputRecording) stopOutputRecording()\n'
            '        stopMicForegroundService()\n')
    if pair in text:
        text = re.sub(r'(?:' + re.escape(pair) + r'){2,}', pair, text)
    return text


def normalize_beat_fx_controller(text: str) -> str:
    # Beat FX must be immediately usable and still have an explicit UI bypass.
    text = text.replace(
        '    var beatFxEnabled by mutableStateOf(false)\n',
        '    var beatFxEnabled by mutableStateOf(true)\n',
        1,
    )
    return text


def normalize_main(text: str) -> str:
    # Keep exactly one recording spacer and the explicit parameterized calls.
    text = text.replace('                Spacer(Modifier.height(10.dp))\n                Spacer(Modifier.height(10.dp))\n', '                Spacer(Modifier.height(10.dp))\n', 1)
    text = re.sub(r'micController\.savePendingRecording\(uri\)', 'micController.savePendingRecording(uri, recordingFormat)', text)
    text = re.sub(r'micController\.suggestedRecordingName\(\)', 'micController.suggestedRecordingName(recordingFormat)', text)

    old = '''        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n            Column(Modifier.padding(14.dp)) {\n                Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)\n                Spacer(Modifier.height(6.dp))\n                Text("BPM: ${micController.bpm.toInt()}")\n                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)\n                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    items(BeatFxDivision.values().toList()) { div -> FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) }) }\n                }\n            }\n        }'''
    new = '''        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n            Column(Modifier.padding(14.dp)) {\n                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {\n                    Column(Modifier.weight(1f)) {\n                        Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)\n                        Text(if (micController.beatFxEnabled) "ACTIVE • synced to BPM" else "BYPASSED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                    Switch(checked = micController.beatFxEnabled, onCheckedChange = { micController.beatFxEnabled = it })\n                }\n                Spacer(Modifier.height(6.dp))\n                Text("BPM: ${micController.bpm.toInt()}")\n                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)\n                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    items(BeatFxDivision.values().toList()) { div -> FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) }) }\n                }\n            }\n        }'''
    if old not in text:
        raise SystemExit("Beat FX UI block not found")
    text = text.replace(old, new, 1)
    return text


def main():
    mic = MIC.read_text(encoding="utf-8")
    main_text = MAIN.read_text(encoding="utf-8")
    mic = remove_duplicate_toggle_functions(mic)
    mic = normalize_recording_api(mic)
    mic = normalize_stop_mic(mic)
    mic = normalize_beat_fx_controller(mic)
    main_text = normalize_main(main_text)
    MIC.write_text(mic, encoding="utf-8")
    MAIN.write_text(main_text, encoding="utf-8")
    print("Microphone source normalized idempotently: Beat FX + stopMic cleanup")


if __name__ == "__main__":
    main()
