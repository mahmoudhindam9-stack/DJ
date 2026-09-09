import os
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    filename = os.path.basename(filepath).replace('.kt', '')

    # Replace try { ... } catch (_: Exception) {} where {} is on the same line
    # We'll use a function for substitution
    def repl_exception(m):
        return m.group(1) + f"catch (e: Exception) {{ android.util.Log.w(\"{filename}\", \"Caught exception\", e)" + m.group(2)

    def repl_throwable(m):
        return m.group(1) + f"catch (e: Throwable) {{ android.util.Log.w(\"{filename}\", \"Caught throwable\", e)" + m.group(2)

    new_content = re.sub(r'(try\s*\{.*?\s*\}\s*)catch\s*\(\s*_\s*:\s*Exception\s*\)\s*\{', repl_exception, content)
    new_content = re.sub(r'(try\s*\{.*?\s*\}\s*)catch\s*\(\s*_\s*:\s*Throwable\s*\)\s*\{', repl_throwable, new_content)
    
    # Also replace standard catch(_: Exception) {
    def repl_ex2(m):
        return f"catch (e: Exception) {{\n            android.util.Log.w(\"{filename}\", \"Caught exception\", e)"
    def repl_th2(m):
        return f"catch (e: Throwable) {{\n            android.util.Log.w(\"{filename}\", \"Caught throwable\", e)"
        
    new_content = re.sub(r'catch\s*\(\s*_\s*:\s*Exception\s*\)\s*\{', repl_ex2, new_content)
    new_content = re.sub(r'catch\s*\(\s*_\s*:\s*Throwable\s*\)\s*\{', repl_th2, new_content)

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Fixed {filepath}")

for root, _, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            process_file(os.path.join(root, file))
