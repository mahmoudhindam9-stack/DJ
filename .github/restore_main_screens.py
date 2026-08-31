from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
GOOD_COMMIT = "1fc1e05de01a6d50b7ad5a538a3b54e59f0e1e7d"
GOOD_PATH = "app/src/main/java/com/example/MainActivity.kt"

subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", GOOD_COMMIT], cwd=ROOT, check=True)
content = subprocess.check_output(["git", "show", f"FETCH_HEAD:{GOOD_PATH}"], cwd=ROOT, text=True)
MAIN.write_text(content, encoding="utf-8")
print("Restored complete MainActivity.kt from known-good baseline")
