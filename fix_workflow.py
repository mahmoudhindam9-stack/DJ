import re

with open('.github/workflows/auto-release.yml', 'r') as f:
    yaml = f.read()

yaml = yaml.replace('- main', '- main\n      - master')
yaml = yaml.replace("date +'%Y%m%d%H%M'", "date +'%Y%m%d%H%M%S'")
yaml = yaml.replace("softprops/action-gh-release@v1", "softprops/action-gh-release@v2")

with open('.github/workflows/auto-release.yml', 'w') as f:
    f.write(yaml)
