$(python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/example/MainActivity.kt')
t=p.read_text(encoding='utf-8')
needle='@Composable\n// KARAOKE_DJ_ENGLISH_V2\nfun MicScreen('
replacement='@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\n// KARAOKE_DJ_ENGLISH_V2\nfun MicScreen('
if needle not in t: raise SystemExit('MicScreen anchor not found')
p.write_text(t.replace(needle,replacement,1),encoding='utf-8')
PY)