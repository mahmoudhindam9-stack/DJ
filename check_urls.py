import urllib.request
import re

with open('app/src/main/java/com/example/djfx/FactoryFxCatalog.kt', 'r') as f:
    content = f.read()

urls = re.findall(r'"(https?://.*?)"', content)

for url in urls:
    req = urllib.request.Request(url, method="HEAD")
    try:
        response = urllib.request.urlopen(req)
        if response.status >= 400:
            print(f"FAILED {response.status}: {url}")
    except Exception as e:
        print(f"FAILED {e}: {url}")
