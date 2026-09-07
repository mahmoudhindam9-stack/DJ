import re

for filename in ['app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt', 'app/src/main/java/com/example/widget/MusicWidgetProvider.kt']:
    with open(filename, 'r') as f:
        text = f.read()
    
    text = text.replace('PendingIntent.getService(context, base, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_PREV), flags)', 
                        'PendingIntent.getBroadcast(context, base, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_PREV), flags)')
    text = text.replace('PendingIntent.getService(context, base + 1, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), flags)', 
                        'PendingIntent.getBroadcast(context, base + 1, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), flags)')
    text = text.replace('PendingIntent.getService(context, base + 2, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_NEXT), flags)', 
                        'PendingIntent.getBroadcast(context, base + 2, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_NEXT), flags)')
    
    with open(filename, 'w') as f:
        f.write(text)

with open('app/src/main/AndroidManifest.xml', 'r') as f:
    text = f.read()

if "WidgetActionReceiver" not in text:
    text = text.replace('</application>', '    <receiver android:name=".widget.WidgetActionReceiver" android:exported="false" />\n    </application>')
    with open('app/src/main/AndroidManifest.xml', 'w') as f:
        f.write(text)

