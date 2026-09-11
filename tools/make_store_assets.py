"""Play ストア用の画像を生成する。

アプリ内のアダプティブアイコンと同じ意匠・同じ配色で、ストアが要求する
寸法の PNG を作る。手作業で作り直すと本体と食い違うので、スクリプトにしてある。

  - store-icon-512.png       : 512x512、アプリアイコン（角丸なし・透過なし）
  - feature-graphic-1024.png : 1024x500、掲載ページ上部の帯

実行: python tools/make_store_assets.py [出力先ディレクトリ]
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

# アプリの配色 (ui/theme/Theme.kt と揃える)
CLAY = (139, 94, 60)
PAPER = (246, 241, 232)
PAGE_EDGE = (217, 203, 184)
RIBBON = (181, 83, 63)
INK = (59, 50, 44)
SAND = (246, 241, 232)

# 可変フォント (NotoSansJP-VF) は既定の太さが細く、帯の中で弱く見える。
# 見出しには太字のフォントを明示的に選ぶ。
BOLD_FONTS = [
    r"C:\Windows\Fonts\YuGothB.ttc",
    r"C:\Windows\Fonts\meiryob.ttc",
    r"C:\Windows\Fonts\NotoSansJP-VF.ttf",
]
REGULAR_FONTS = [
    r"C:\Windows\Fonts\YuGothM.ttc",
    r"C:\Windows\Fonts\meiryo.ttc",
    r"C:\Windows\Fonts\NotoSansJP-VF.ttf",
]


def load_font(size, bold=False):
    for path in (BOLD_FONTS if bold else REGULAR_FONTS):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def draw_book(draw, cx, cy, width, height):
    """栞をはさんだ本。アプリのアイコンと同じ形。"""
    left = cx - width / 2
    top = cy - height / 2
    right = cx + width / 2
    bottom = cy + height / 2
    radius = width * 0.09

    # 本体
    draw.rounded_rectangle([left, top, right, bottom], radius=radius, fill=PAPER)
    # 小口（ページの重なり）
    edge_w = width * 0.11
    draw.rounded_rectangle(
        [right - edge_w, top, right, bottom], radius=radius, fill=PAGE_EDGE
    )
    draw.rectangle([right - edge_w, top + radius, right - edge_w * 0.4, bottom - radius],
                   fill=PAGE_EDGE)
    # 背表紙側の罫
    spine_x = left + width * 0.14
    draw.rectangle([spine_x, top, spine_x + width * 0.035, bottom], fill=PAGE_EDGE)

    # 栞
    rb_w = width * 0.20
    rb_left = cx - rb_w / 2 - width * 0.06
    rb_bottom = top + height * 0.52
    notch = height * 0.09
    draw.polygon(
        [
            (rb_left, top),
            (rb_left + rb_w, top),
            (rb_left + rb_w, rb_bottom),
            (rb_left + rb_w / 2, rb_bottom - notch),
            (rb_left, rb_bottom),
        ],
        fill=RIBBON,
    )


def make_icon(path, size=512):
    img = Image.new("RGB", (size, size), CLAY)
    draw = ImageDraw.Draw(img)
    # アイコンは円形などにマスクされるため、図形を中央 66% の安全領域に収める
    draw_book(draw, size / 2, size / 2, size * 0.42, size * 0.52)
    img.save(path, "PNG")
    return path


def make_feature_graphic(path, width=1024, height=500):
    img = Image.new("RGB", (width, height), SAND)
    draw = ImageDraw.Draw(img)

    # 左側に帯状の色面を敷き、その上に本を置く
    draw.rectangle([0, 0, width * 0.34, height], fill=CLAY)
    draw_book(draw, width * 0.17, height / 2, height * 0.46, height * 0.58)

    title_font = load_font(68, bold=True)
    body_font = load_font(31, bold=True)
    small_font = load_font(24)

    x = width * 0.40
    draw.text((x, height * 0.26), "ヨムメモ", font=title_font, fill=INK)
    draw.text((x, height * 0.47), "バーコードで登録、", font=body_font, fill=INK)
    draw.text((x, height * 0.58), "気づいたその場で書き留める。", font=body_font, fill=INK)
    draw.text((x, height * 0.74), "読書メモ / 引用と自分の言葉を分けて残せる",
              font=small_font, fill=(122, 111, 99))

    img.save(path, "PNG")
    return path


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "store-assets"
    os.makedirs(out_dir, exist_ok=True)
    icon = make_icon(os.path.join(out_dir, "store-icon-512.png"))
    feature = make_feature_graphic(os.path.join(out_dir, "feature-graphic-1024.png"))
    for p in (icon, feature):
        with Image.open(p) as im:
            print("{}  {}x{}".format(p, im.width, im.height))


if __name__ == "__main__":
    main()
