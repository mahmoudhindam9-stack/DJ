import os
import re

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r') as f:
                content = f.read()
            
            # Replace catch (e: Throwable)
            new_content = re.sub(r'catch\s*\(\s*([a-zA-Z]+)\s*:\s*Throwable\s*\)', r'catch (\1: Exception)', content)
            
            if new_content != content:
                with open(path, 'w') as f:
                    f.write(new_content)
