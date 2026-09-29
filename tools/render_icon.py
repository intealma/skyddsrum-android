"""Render the 1024x1024 store icon: the website's line mark (square + triangle), white on black.
Same geometry as res/drawable/ic_launcher_foreground.xml (108-unit adaptive-icon viewport)."""
from pathlib import Path
from PIL import Image, ImageDraw

SIZE, SS = 1024, 4                  # output size, supersampling factor
VIEW_MIN, VIEW_SPAN = 21.0, 66.0    # crop of the 108-unit viewport so the mark fills the store icon
k = SIZE * SS / VIEW_SPAN

def tx(x, y):
    return ((x - VIEW_MIN) * k, (y - VIEW_MIN) * k)

img = Image.new("RGB", (SIZE * SS, SIZE * SS), (0, 0, 0))
d = ImageDraw.Draw(img)
w = round(3 * k)
d.rounded_rectangle([tx(34, 34), tx(74, 74)], radius=2.7 * k, outline=(255, 255, 255), width=w)
tri = [tx(54, 43.3), tx(67.3, 63.3), tx(40.7, 63.3)]
d.line(tri + [tri[0], tri[1]], fill=(255, 255, 255), width=w, joint="curve")
out = Path(__file__).resolve().parent.parent / "art/icon-1024.png"
img.resize((SIZE, SIZE), Image.LANCZOS).save(out)
print("Wrote", out)
