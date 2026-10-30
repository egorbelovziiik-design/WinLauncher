"""Procedural texture generation for DEAD WORLD: APOCALYPSE (PIL + numpy).
Run: python tools/gen_textures.py  -> assets/textures/*.png
All textures are original, generated from noise - no external assets."""
import os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "textures")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1337)
random.seed(1337)


def save(img, name):
    img.save(os.path.join(OUT, name))
    print("texture:", name, img.size)


def noise_tex(size, base, variation, blotch=0.0, blotch_color=None):
    arr = np.zeros((size, size, 3), dtype=np.float32)
    noise = rng.normal(0, variation, (size, size, 1))
    for i, c in enumerate(base):
        arr[:, :, i] = c + noise[:, :, 0]
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    if blotch > 0:
        d = ImageDraw.Draw(img, "RGBA")
        for _ in range(int(blotch * 60)):
            x, y = random.randrange(size), random.randrange(size)
            r = random.randrange(4, 42)
            d.ellipse([x - r, y - r, x + r, y + r], fill=blotch_color + (random.randrange(30, 110),))
        img = img.filter(ImageFilter.GaussianBlur(1.2))
    return img


def grass():
    img = noise_tex(256, (62, 84, 38), 16, blotch=0.8, blotch_color=(52, 66, 26))
    d = ImageDraw.Draw(img)
    for _ in range(2600):
        x, y = random.randrange(256), random.randrange(256)
        c = random.choice([(78, 104, 44), (44, 66, 30), (96, 110, 52), (70, 78, 34)])
        d.line([x, y, x + random.randint(-2, 2), y - random.randint(2, 6)], fill=c)
    save(img, "grass.png")


def asphalt():
    img = noise_tex(256, (52, 52, 56), 9, blotch=1.0, blotch_color=(38, 38, 40))
    d = ImageDraw.Draw(img)
    for _ in range(26):  # cracks
        x, y = random.randrange(256), random.randrange(256)
        for _ in range(random.randint(4, 14)):
            nx, ny = x + random.randint(-14, 14), y + random.randint(-14, 14)
            d.line([x, y, nx, ny], fill=(26, 26, 28), width=1)
            x, y = nx, ny
    save(img, "asphalt.png")


def road_marking():
    img = noise_tex(256, (48, 48, 52), 8)
    d = ImageDraw.Draw(img)
    for y in range(0, 256, 64):
        d.rectangle([122, y + 6, 134, y + 40], fill=(178, 172, 140))
    save(img, "road.png")


def concrete():
    img = noise_tex(256, (118, 116, 110), 11, blotch=1.2, blotch_color=(92, 90, 84))
    save(img, "concrete.png")


def brick():
    img = Image.new("RGB", (256, 256), (96, 88, 80))
    d = ImageDraw.Draw(img)
    bh, bw = 16, 42
    for row, y in enumerate(range(0, 256, bh)):
        off = (bw // 2) if row % 2 else 0
        for x in range(-bw, 256, bw):
            base = random.choice([(122, 70, 54), (110, 62, 48), (132, 78, 58), (100, 58, 46)])
            d.rectangle([x + off + 1, y + 1, x + off + bw - 1, y + bh - 1], fill=base)
    arr = np.array(img).astype(np.float32) + rng.normal(0, 8, (256, 256, 1))
    save(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)), "brick.png")


def dirt():
    img = noise_tex(256, (86, 68, 48), 14, blotch=1.4, blotch_color=(64, 50, 34))
    save(img, "dirt.png")


def blood_splat():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = 64, 64
    for _ in range(46):
        a = random.uniform(0, 6.283)
        dist = random.uniform(0, 44) ** 1.2
        x, y = cx + dist * np.cos(a), cy + dist * np.sin(a)
        r = max(1.5, 14 - dist * 0.24 + random.uniform(-3, 3))
        d.ellipse([x - r, y - r, x + r, y + r], fill=(96, 8, 8, random.randrange(120, 235)))
    save(img, "blood_splat.png")


def muzzle():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = 64, 64
    for ang in range(0, 360, 45):
        a = np.radians(ang + random.randint(-8, 8))
        L = random.randint(34, 58)
        pts = [(cx, cy), (cx + L * np.cos(a - 0.16), cy + L * np.sin(a - 0.16)),
               (cx + (L + 14) * np.cos(a), cy + (L + 14) * np.sin(a)),
               (cx + L * np.cos(a + 0.16), cy + L * np.sin(a + 0.16))]
        d.polygon(pts, fill=(255, 196, 84, 200))
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=(255, 240, 200, 255))
    save(img.filter(ImageFilter.GaussianBlur(1.5)), "muzzle_flash.png")


def scratches():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(3):
        x0 = 30 + i * 70
        pts = [(x0 + random.randint(-6, 6), 10)]
        for y in range(40, 250, 40):
            pts.append((x0 + 26 + random.randint(-8, 8), y))
        d.line(pts, fill=(120, 10, 10, 190), width=5)
    save(img, "claw_marks.png")


def sky_gradient(name, top, bottom):
    img = Image.new("RGB", (64, 256))
    d = ImageDraw.Draw(img)
    for y in range(256):
        t = y / 255.0
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([0, y, 64, y], fill=c)
    save(img, name)


def wood():
    img = noise_tex(256, (112, 80, 50), 7)
    d = ImageDraw.Draw(img)
    for y in range(0, 256, 3):
        c = int(18 * abs(np.sin(y * 0.11)))
        d.line([0, y, 256, y], fill=(112 - c, 80 - c, 50 - c // 2))
    for _ in range(8):
        x, y = random.randrange(256), random.randrange(256)
        for r in range(2, 14, 3):
            d.ellipse([x - r, y - r // 2, x + r, y + r // 2], outline=(78, 54, 32))
    save(img, "wood.png")


def rust_metal():
    img = noise_tex(256, (96, 98, 102), 10, blotch=1.6, blotch_color=(128, 76, 44))
    save(img, "rust_metal.png")


def leaves():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(420):
        x, y = random.randrange(256), random.randrange(256)
        r = random.randint(4, 11)
        c = random.choice([(48, 84, 30, 255), (62, 102, 38, 255), (36, 66, 24, 255), (84, 96, 34, 255)])
        d.ellipse([x, y, x + r, y + r // 2], fill=c)
    save(img, "leaves.png")


def sign(text, name, bg, fg):
    img = Image.new("RGB", (512, 128), bg)
    d = ImageDraw.Draw(img)
    d.rectangle([4, 4, 508, 124], outline=fg, width=4)
    # blocky letters via rectangles is overkill; use default font scaled by drawing big text
    try:
        from PIL import ImageFont
        font = ImageFont.load_default(size=56)
    except Exception:
        font = None
    d.text((256, 64), text, fill=fg, anchor="mm", font=font)
    save(img, name)


def main():
    grass(); asphalt(); road_marking(); concrete(); brick(); dirt()
    blood_splat(); muzzle(); scratches(); wood(); rust_metal(); leaves()
    sky_gradient("sky_day.png", (96, 140, 190), (196, 214, 228))
    sky_gradient("sky_night.png", (4, 6, 18), (22, 30, 52))
    sign("HOSPITAL", "sign_hospital.png", (225, 225, 220), (178, 32, 32))
    sign("POLICE", "sign_police.png", (24, 34, 74), (230, 230, 235))
    sign("SHOP 24H", "sign_shop.png", (140, 96, 20), (250, 240, 210))
    sign("GAS", "sign_gas.png", (150, 30, 24), (245, 240, 230))
    sign("MILITARY ZONE", "sign_military.png", (52, 64, 40), (220, 224, 200))
    print("TEXTURES_DONE")


if __name__ == "__main__":
    main()
