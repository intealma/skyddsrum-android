"""Render the 1024x1024 app icon (same geometry as res/drawable/ic_launcher_foreground.xml)."""
from pathlib import Path
from PIL import Image, ImageDraw

SIZE, SS = 1024, 4                 # output size, supersampling factor
VIEW_MIN, VIEW_SPAN = 14.0, 80.0   # crop of the 108-unit adaptive-icon viewport
TEAL, AMBER = (0x12, 0x3C, 0x44), (0xF4, 0xB9, 0x42)

def cubic(p0, p1, p2, p3, n=48):
    for i in range(1, n + 1):
        t = i / n
        u = 1 - t
        yield (u**3 * p0[0] + 3 * u*u*t * p1[0] + 3 * u*t*t * p2[0] + t**3 * p3[0],
               u**3 * p0[1] + 3 * u*u*t * p1[1] + 3 * u*t*t * p2[1] + t**3 * p3[1])

def tx(pts):
    k = SIZE * SS / VIEW_SPAN
    return [((x - VIEW_MIN) * k, (y - VIEW_MIN) * k) for x, y in pts]

shield = [(54, 24), (78, 32), (78, 54)]
shield += cubic((78, 54), (78, 68), (68, 78), (54, 84))
shield += cubic((54, 84), (40, 78), (30, 68), (30, 54))
shield += [(30, 32)]
house = [(38, 54), (54, 40), (70, 54), (66, 54), (66, 70), (42, 70), (42, 54)]
door = [(50, 70), (50, 60), (58, 60), (58, 70)]

img = Image.new("RGB", (SIZE * SS, SIZE * SS), TEAL)
d = ImageDraw.Draw(img)
d.polygon(tx(shield), fill=AMBER)
d.polygon(tx(house), fill=TEAL)
d.polygon(tx(door), fill=AMBER)
out = Path(__file__).resolve().parent.parent / "art/icon-1024.png"
img.resize((SIZE, SIZE), Image.LANCZOS).save(out)
print("Wrote", out)
