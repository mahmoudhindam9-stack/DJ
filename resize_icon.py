from PIL import Image

path = "app/src/main/res/drawable/app_icon_fg_1788186043958.jpg"
img = Image.open(path)
img = img.resize((256, 256), Image.Resampling.LANCZOS)
img.save(path, optimize=True, quality=85)
