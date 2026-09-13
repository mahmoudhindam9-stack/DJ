import re

with open('.github/workflows/android-build.yml', 'r') as f:
    content = f.read()

# Remove the python script runs
content = re.sub(r'\s*- name: Apply global app-wide EQ routing\s*shell: bash\s*run: python3 \.github/fix_global_equalizer_routing\.py', '', content)
content = re.sub(r'\s*- name: Apply DJ UI-only polish\s*shell: bash\s*run: python3 \.github/dj_ui_polish\.py', '', content)

with open('.github/workflows/android-build.yml', 'w') as f:
    f.write(content)

print("Updated android-build.yml")
