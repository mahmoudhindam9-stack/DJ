import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Clean up the end of the file
# Remove the stray `    }\n}` at the end
content = re.sub(r'\}\s*\}\s*\}\s*$', '}\n', content)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
