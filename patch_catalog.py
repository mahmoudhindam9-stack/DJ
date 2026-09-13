with open("app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "r") as f:
    text = f.read()

text = text.replace('"شرقي"', '"Oriental"')
text = text.replace('"كوميدي"', '"Comedy"')
text = text.replace('"تريندات"', '"Trends"')

with open("app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "w") as f:
    f.write(text)
