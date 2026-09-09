import os
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    filename = os.path.basename(filepath).replace('.kt', '')

    def repl(m):
        exc_type = m.group(1)
        body = m.group(2)
        tag = filename
        if not body.strip():
            # empty body
            new_body = f" android.util.Log.w(\"{tag}\", \"Caught {exc_type.lower()}\", e) "
        else:
            # prepend log statement to body
            # body might be " false " or "  "
            new_body = f" android.util.Log.w(\"{tag}\", \"Caught {exc_type.lower()}\", e); {body.strip()} "
        
        return f"catch (e: {exc_type}) {{{new_body}}}"

    new_content = re.sub(r'catch\s*\(\s*_\s*:\s*(Throwable|Exception)\s*\)\s*\{(.*?)\}', repl, content, flags=re.DOTALL)

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Fixed {filepath}")

for root, _, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            process_file(os.path.join(root, file))
