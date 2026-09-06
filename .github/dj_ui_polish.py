from pathlib import Path

TARGET = Path("app/src/main/java/com/example/MainActivity.kt")

HORIZONTAL_TIMER = '''            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 44.dp),
                    maxLines = 1
                )
                OutlinedButton(
                    onClick = {
                        val newPos = (deck.currentPositionMs - 5000L).coerceAtLeast(0L)
                        deck.seekTo(newPos)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("-5s", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        val newPos = (deck.currentPositionMs + 5000L).coerceAtMost(deck.durationMs)
                        deck.seekTo(newPos)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("+5s", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    com.example.utils.MusicScanner.formatMs(deck.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 44.dp),
                    maxLines = 1
                )
            }
'''

text = TARGET.read_text(encoding="utf-8")
marker = "text = com.example.utils.MusicScanner.formatMs(deck.currentPositionMs)"
first = text.find(marker)
if first < 0:
    raise SystemExit("Shared DJ deck timer marker not found; refusing UI rewrite")
if text.find(marker, first + 1) >= 0:
    raise SystemExit("DJ deck timer marker unexpectedly appears more than once; refusing partial UI rewrite")
row_start = text.rfind("            Row(", 0, first)
row_end = text.find("\n            }\n", first)
if row_start < 0 or row_end < 0:
    raise SystemExit("Could not isolate the shared DJ deck timer row; refusing partial UI rewrite")
replacement_end = row_end + len("\n            }\n")
text = text[:row_start] + HORIZONTAL_TIMER + text[replacement_end:]
TARGET.write_text(text, encoding="utf-8")
print("Converted the shared DJ deck timer to a horizontal time / -5s / +5s / duration layout.")
