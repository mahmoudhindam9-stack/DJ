import re

with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    xml = f.read()

# The first one is the one we want to keep (at the top).
# The second one is at the bottom.
lines = xml.split('\n')
new_lines = []
skip = False
for line in lines:
    if 'android:id="@+id/weather_city"' in line and 'match_parent' in xml[xml.find(line):xml.find(line)+100]:
        # we can just use string replace on the exact snippet
        pass

# Actually, let's just use regex on the whole content
old_city_regex = r'<TextView\s+android:id="@+id/weather_city"\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content"\s+android:visibility="gone"\s*/>'
xml = re.sub(old_city_regex, '', xml, flags=re.DOTALL)

# Let's try simpler:
old_city_regex2 = r'<TextView\s*android:id="@+id/weather_city"\s*android:layout_width="match_parent"\s*android:layout_height="wrap_content"\s*android:visibility="gone"\s*/>'
xml = re.sub(old_city_regex2, '', xml, flags=re.DOTALL)

with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
    f.write(xml)
