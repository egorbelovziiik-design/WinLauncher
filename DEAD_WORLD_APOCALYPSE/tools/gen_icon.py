"""Generate the game icon (dark, red biohazard-ish emblem + DW)."""
import os
from PIL import Image, ImageDraw, ImageFont
OUT = os.path.join(os.path.dirname(__file__), "..", "build")
S = 256
img = Image.new("RGBA", (S, S), (12, 10, 12, 255))
d = ImageDraw.Draw(img)
d.ellipse([14, 14, S - 14, S - 14], outline=(200, 36, 30, 255), width=12)
d.ellipse([86, 86, S - 86, S - 86], fill=(200, 36, 30, 255))
import math
for a in (90, 210, 330):
    r = math.radians(a)
    cx, cy = S / 2 + math.cos(r) * 62, S / 2 + math.sin(r) * 62
    d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=(200, 36, 30, 255))
    d.ellipse([cx - 16, cy - 16, cx + 16, cy + 16], fill=(12, 10, 12, 255))
try:
    font = ImageFont.truetype(os.path.join(os.path.dirname(__file__), "..", "assets", "fonts", "DejaVuSans-Bold.ttf"), 54)
except Exception:
    font = ImageFont.load_default()
d.text((S / 2, S / 2 + 2), "DW", anchor="mm", font=font, fill=(245, 240, 230, 255))
img.save(os.path.join(OUT, "icon.png"))
img.save(os.path.join(OUT, "icon.ico"), sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
print("ICON_DONE")
