with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('Row(\n                ,\n', 'Row(\n                modifier = Modifier.fillMaxWidth(),\n')
text = text.replace('onClick = { deck.toggleEffect(effect.id) },\n                            \n                        )', 'onClick = { deck.toggleEffect(effect.id) }\n                        )')

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'w') as f:
    f.write(text)
