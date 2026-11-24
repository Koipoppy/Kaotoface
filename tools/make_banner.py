"""Build the README banner in the app's white / black / Jost visual language."""
from PIL import Image, ImageDraw, ImageFont

ROOT = "."
JOST = f"{ROOT}/app/src/main/res/font/jost.ttf"
HAN_BOLD = f"{ROOT}/app/src/main/res/font/source_han_sans_bold.otf"
PHOSPHOR = f"{ROOT}/app/src/main/res/font/phosphor_fill.ttf"

W, H = 2000, 560
INK = (10, 10, 10)
MID = (92, 92, 92)
SOFT = (142, 142, 142)
WHITE = (255, 255, 255)

image = Image.new("RGB", (W, H), WHITE)
draw = ImageDraw.Draw(image)

wordmark = ImageFont.truetype(JOST, 108)
tagline = ImageFont.truetype(HAN_BOLD, 40)
small = ImageFont.truetype(JOST, 18)
icons = ImageFont.truetype(PHOSPHOR, 44)

# Left-hand wordmark and tagline.
draw.text((120, 112), "KaoToFace", font=wordmark, fill=INK)
draw.text((124, 278), "让每一句话，都被听懂。", font=tagline, fill=INK)

# Letter-spaced micro line.
x = 124
for ch in "REAL-TIME  BILINGUAL  TRANSLATION":
    draw.text((x, 384), ch, font=small, fill=SOFT)
    x += draw.textlength(ch, font=small) + 2.2

# The ear button is taken from the app itself so it is pixel-identical.
ear = Image.open(f"{ROOT}/docs/portrait.png").convert("RGB").crop((465, 1124, 615, 1274))
ear = ear.resize((196, 196), Image.LANCZOS)
image.paste(ear, (1402, 182))

# Small controls flanking the button, using the same Phosphor Fill glyphs.
left_icons = [0xE272, 0xE34E, 0xE68E]   # settings, records, room
right_icons = [0xE5A2, 0xE094, 0xE136]  # rows, curved swap, caret down
for i, cp in enumerate(left_icons):
    draw.text((1180 + i * 74, 280), chr(cp), font=icons, fill=INK, anchor="mm")
for i, cp in enumerate(right_icons):
    draw.text((1632 + i * 74, 280), chr(cp), font=icons, fill=INK, anchor="mm")

image.save(f"{ROOT}/docs/banner.png", optimize=True)
print("wrote docs/banner.png", image.size)
