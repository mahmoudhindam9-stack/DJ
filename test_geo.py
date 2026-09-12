import urllib.request, json
try:
    req = urllib.request.Request("https://get.geojs.io/v1/ip/geo.json", headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read())
        print("Lat:", data.get("latitude"), "Lon:", data.get("longitude"), "City:", data.get("city"))
except Exception as e:
    print(e)
