with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('import androidx.compose.foundation.lazy.grid.items\n', '')
text = text.replace('modifier = Modifier.fillMaxWidth()', '') # Remove fillMaxWidth from EffectTile so it can scroll properly

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'w') as f:
    f.write(text)
