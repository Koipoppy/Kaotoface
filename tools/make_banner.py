"""Build the README banner in the app's white / black / Jost visual language.

The artwork is rendered at 3000x840 so GitHub can downscale it sharply. The
two lowercase o's are measured glyph boxes at this exact font size and covered
by solid circles in the app's running red.
"""
from PIL import Image, ImageDraw, ImageFont

ROOT = "."
JOST = f"{ROOT}/app/src/main/res/font/jost.ttf"
HAN_BOLD = f"{ROOT}/app/src/main/res/font/source_han_sans_bold.otf"
PHOSPHOR = f"{ROOT}/app/src/main/res/font/phosphor_fill.ttf"

W, H = 3000, 840
INK = (10, 10, 10)
SOFT = (142, 142, 142)
WHITE = (255, 255, 255)
RED = (217, 45, 32)

image = Image.new("RGB", (W, H), WHITE)
draw = ImageDraw.Draw(image)

wordmark = ImageFont.truetype(JOST, 162)
tagline = ImageFont.truetype(HAN_BOLD, 60)
small = ImageFont.truetype(JOST, 27)
icons = ImageFont.truetype(PHOSPHOR, 66)

# Wordmark.
draw.text((180, 168), "KaoToFace", font=wordmark, fill=INK)

# Measured boxes of the two o glyphs in this exact Jost rendering.
# They are covered by supersampled solid circles below.
o_boxes = [(355, 266, 431, 343), (487, 266, 563, 343)]
overlay = Image.new("RGBA", (W * 2, H * 2), (0, 0, 0, 0))
od = ImageDraw.Draw(overlay)
for x0, y0, x1, y1 in o_boxes:
    cx = (x0 + x1) * 2 / 2
    cy = (y0 + y1) * 2 / 2
    r = (max(x1 - x0, y1 - y0) / 2 + 1.2) * 2
    od.ellipse((cx - r, cy - r, cx + r, cy + r), fill=RED + (255,))
overlay = overlay.resize((W, H), Image.LANCZOS)
image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
draw = ImageDraw.Draw(image)

# Tagline and micro line.
draw.text((186, 417), "让每一句话，都被听懂。", font=tagline, fill=INK)
x = 186
for ch in "REAL-TIME  BILINGUAL  TRANSLATION":
    draw.text((x, 576), ch, font=small, fill=SOFT)
    x += draw.textlength(ch, font=small) + 3.3

# The ear button is a 2x app capture, so it stays sharp at this size.
ear = Image.open(f"{ROOT}/docs/ear.png").convert("RGB").resize((294, 294), Image.LANCZOS)
image.paste(ear, (2103, 273))

# Phosphor Fill controls, same glyphs as the app.
left_icons = [0xE272, 0xE34E, 0xE68E]   # settings, records, room
right_icons = [0xE5A2, 0xE094, 0xE136]  # rows, curved swap, caret down
for i, cp in enumerate(left_icons):
    draw.text((1770 + i * 111, 420), chr(cp), font=icons, fill=INK, anchor="mm")
for i, cp in enumerate(right_icons):
    draw.text((2448 + i * 111, 420), chr(cp), font=icons, fill=INK, anchor="mm")

image.save(f"{ROOT}/docs/hero.png", optimize=True)
print("wrote docs/hero.png", image.size)
