#!/usr/bin/env python3
"""키즈링고 런처 아이콘 만들기.

원본 그림(icon-source.png)에서 안드로이드 런처 아이콘 세트를 만든다.

- res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png, ic_launcher_round.png (레거시 아이콘)
- res/drawable/ic_launcher_foreground.png (적응형 아이콘 전경 432x432)
- www/icons/icon-192.png, icon-512.png (웹 앱 아이콘)

사용: python3 android/tools/make_icons.py [원본.png]
"""
from PIL import Image, ImageDraw
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID_DIR = os.path.dirname(HERE)
REPO = os.path.dirname(ANDROID_DIR)
RES = os.path.join(ANDROID_DIR, "app/src/main/res")

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "icon-source.png")

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BG = (98, 199, 15, 255)          # 아이콘 그림의 초록 배경 (#62C70F)
CORNER = 0.20                    # 둥근 모서리 반경 비율


def rounded_mask(size, radius_ratio=CORNER, supersample=4):
    m = Image.new("L", (size * supersample, size * supersample), 0)
    d = ImageDraw.Draw(m)
    r = int(size * supersample * radius_ratio)
    d.rounded_rectangle([0, 0, size * supersample - 1, size * supersample - 1], radius=r, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def circle_mask(size, supersample=4):
    m = Image.new("L", (size * supersample, size * supersample), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([0, 0, size * supersample - 1, size * supersample - 1], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def square_artwork(src):
    """그림의 흰 모서리를 앱 배경색으로 채워 꽉 찬 정사각형 그림으로 만든다."""
    size = max(src.size)
    src = src.convert("RGBA")
    if src.size != (size, size):
        canvas = Image.new("RGBA", (size, size), BG)
        canvas.alpha_composite(src, ((size - src.width) // 2, (size - src.height) // 2))
        src = canvas
    solid = Image.new("RGBA", (size, size), BG)
    solid.alpha_composite(src)
    masked = Image.new("RGBA", (size, size), BG)
    masked.paste(solid, (0, 0), rounded_mask(size))
    return masked


def legacy(art, size, mask):
    img = art.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    src = Image.open(SRC)
    art = square_artwork(src)

    for name, px in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        legacy(art, px, rounded_mask(px)).save(os.path.join(folder, "ic_launcher.png"))
        legacy(art, px, circle_mask(px)).save(os.path.join(folder, "ic_launcher_round.png"))

    # 적응형 아이콘 전경: 안전 영역(108dp 중 가운데 72dp) 안에 그림을 넣는다.
    canvas = 432
    inner = int(canvas * 0.68)
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    fg.paste(art.resize((inner, inner), Image.LANCZOS), ((canvas - inner) // 2, (canvas - inner) // 2))
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"))

    www_icons = os.path.join(REPO, "www", "icons")
    os.makedirs(www_icons, exist_ok=True)
    for px in (192, 512):
        art.resize((px, px), Image.LANCZOS).save(os.path.join(www_icons, "icon-%d.png" % px))

    print("아이콘 생성 완료:", RES, "->", www_icons)


if __name__ == "__main__":
    main()
