import urllib.request
import os
import re

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

matches = re.findall(r'FactoryFxDownload\("([^"]+)", "([^"]+)"\)', content)
if not matches:
    print("No matches found")
else:
    for rel_path, url in matches:
        target = f'app/src/main/assets/{rel_path}'
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if not os.path.exists(target):
            print(f"Downloading {url} to {target}")
            try:
                urllib.request.urlretrieve(url, target)
            except Exception as e:
                print(f"Error downloading {url}: {e}")

