import re

path = "app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt"
with open(path, 'r') as f:
    content = f.read()

content = re.sub(r'val pct = .*?\n\s*views\.setProgressBar\(R\.id\.widget_progress, 1000, pct, false\)', '', content)
with open(path, 'w') as f:
    f.write(content)
