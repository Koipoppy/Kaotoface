"""Render the launcher icon source (res/drawable XML) without redrawing it.

The app icon is not an image; it is three Android vector drawables. This script
reads those files, translates every path / gradient / stroke into SVG, and lets
Chrome rasterise the result. Nothing about the face is hand-coded here, so the
PNG preview cannot drift away from the source again.

Outputs:
  docs/icon-source.svg           raw adaptive layers (108x108, square)
  docs/icon-source.png           raster of the raw layers
  tools/icon_source_circle.png   same art, Android adaptive-icon circle mask
"""
from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DRAWABLES = ROOT / "app/src/main/res/drawable"
CHROME = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")
DOCS = ROOT / "docs"
TOOLS = ROOT / "tools"

ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

SVG_NS = "http://www.w3.org/2000/svg"


def colour(value: str | None) -> str | None:
    """#AARRGGBB -> #RRGGBB, with Android's transparent sentinel."""
    if not value:
        return None
    value = value.upper()
    if not value.startswith("#") or len(value) != 9:
        return value
    alpha = int(value[1:3], 16)
    if alpha == 0:
        return "none"
    return "#" + value[3:]


def gradient(element: ET.Element, ident: str) -> tuple[str, str]:
    stops = "".join(
        f'<stop offset="{item.get(ANDROID + "offset")}" '
        f'stop-color="{colour(item.get(ANDROID + "color"))}"/>'
        for item in element.findall("item")
    )
    # In vector XML the element is always <gradient>; android:type decides the
    # SVG form. Reading the tag name here would turn linear eye shading radial.
    if element.get(ANDROID + "type") == "linear":
        x1 = element.get(ANDROID + "startX", "0")
        y1 = element.get(ANDROID + "startY", "0")
        x2 = element.get(ANDROID + "endX", "108")
        y2 = element.get(ANDROID + "endY", "108")
        return (
            f'<linearGradient id="{ident}" gradientUnits="userSpaceOnUse" '
            f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>',
            f"url(#{ident})",
        )
    cx = element.get(ANDROID + "centerX", "54")
    cy = element.get(ANDROID + "centerY", "54")
    radius = element.get(ANDROID + "gradientRadius", "54")
    return (
        f'<radialGradient id="{ident}" gradientUnits="userSpaceOnUse" '
        f'cx="{cx}" cy="{cy}" r="{radius}">{stops}</radialGradient>',
        f"url(#{ident})",
    )


def vector_body(path: Path) -> tuple[list[str], str]:
    """Return SVG definitions and body for one Android vector drawable."""
    root = ET.parse(path).getroot()
    defs: list[str] = []
    body: list[str] = []
    for index, path_node in enumerate(root.findall("path")):
        attrs: list[str] = [
            f'd="{path_node.get(ANDROID + "pathData")}"',
        ]

        fill = colour(path_node.get(ANDROID + "fillColor"))
        fill_gradient = path_node.find(f"{AAPT}attr[@name='android:fillColor']")
        if fill_gradient is not None:
            gradient_node = fill_gradient.find("gradient")
            if gradient_node is not None:
                definition, fill = gradient(gradient_node, f"{path.stem}-{index}")
                defs.append(definition)
        if fill:
            attrs.append(f'fill="{fill}"')
        else:
            attrs.append('fill="none"')

        stroke = colour(path_node.get(ANDROID + "strokeColor"))
        if stroke:
            attrs.append(f'stroke="{stroke}"')
            attrs.append(f'stroke-width="{path_node.get(ANDROID + "strokeWidth", "1")}"')
            cap = path_node.get(ANDROID + "strokeLineCap")
            if cap:
                attrs.append(f'stroke-linecap="{cap}"')
            join = path_node.get(ANDROID + "strokeLineJoin")
            if join:
                attrs.append(f'stroke-linejoin="{join}"')

        body.append(f'<path {" ".join(attrs)}/>')
    return defs, "".join(body)


def svg(visible: bool) -> str:
    """Translate the drawables to SVG.

    Android adaptive icons are 108x108, but launchers only show the central
    72x72 safe zone. Rendering the full 108 is what made the old preview's face
    look too small. `visible` crops the viewBox to exactly that 72-unit circle.
    """
    background_defs, background = vector_body(DRAWABLES / "ic_launcher_background.xml")
    foreground_defs, foreground = vector_body(DRAWABLES / "ic_launcher_foreground.xml")
    defs = "".join(background_defs + foreground_defs)
    if visible:
        defs += '<clipPath id="adaptive-mask"><circle cx="54" cy="54" r="36"/></clipPath>'
        content = f'<g clip-path="url(#adaptive-mask)">{background}{foreground}</g>'
        viewbox = "18 18 72 72"
    else:
        content = background + foreground
        viewbox = "0 0 108 108"
    return (
        f'<svg xmlns="{SVG_NS}" width="1024" height="1024" '
        f'viewBox="{viewbox}" shape-rendering="geometricPrecision">'
        "<!-- Generated by tools/render_icon_source.py from "
        "app/src/main/res/drawable/ic_launcher_*.xml. Do not edit by hand. -->"
        f"<defs>{defs}</defs>{content}</svg>"
    )


def rasterise(svg_path: Path, png_path: Path) -> None:
    html = TOOLS / "_icon_render.html"
    html.write_text(
        "<!doctype html><meta charset='utf-8'><style>"
        "html,body{margin:0;width:1024px;height:1024px;overflow:hidden;background:#fff}"
        "svg{display:block}</style>"
        + svg_path.read_text(),
        encoding="utf-8",
    )
    subprocess.run(
        [
            str(CHROME),
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            "--window-size=1024,1024",
            f"--screenshot={png_path}",
            html.as_uri(),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    html.unlink(missing_ok=True)


def main() -> None:
    DOCS.mkdir(exist_ok=True)
    TOOLS.mkdir(exist_ok=True)

    # The visible icon source: the 108 drawables cropped to the 72-unit safe
    # zone every launcher actually shows. source SVG -> source PNG, one pipeline.
    source_svg = DOCS / "icon-source.svg"
    source_png = DOCS / "icon-source.png"
    source_svg.write_text(svg(visible=True), encoding="utf-8")
    rasterise(source_svg, source_png)

    # The source render on the launcher's white tile, at the ratio seen in the
    # drawer. This replaces the old hand-drawn icon_preview.png.
    source = Image.open(source_png).convert("RGB")
    launcher_preview = Image.new("RGB", (1400, 1400), (255, 255, 255))
    launcher_preview.paste(source.resize((1000, 1000), Image.LANCZOS), (200, 200))
    launcher_preview.save(TOOLS / "icon_preview.png")

    # Equal-size face comparison: source render (left) vs the installed APK's
    # launcher rendering, cropped to the same dark circle (right).
    actual = Image.open(TOOLS / "actual_icon_big_nose.png").convert("RGB")
    dark = np.array(actual.convert("L")) < 100
    ys, xs = np.where(dark)
    if len(xs) == 0:
        raise SystemExit("could not find the dark icon in the launcher crop")
    actual_circle = actual.crop(
        (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    )
    actual_circle = actual_circle.resize((1024, 1024), Image.LANCZOS)

    compare = Image.new("RGB", (2048, 1160), (255, 255, 255))
    compare.paste(source, (0, 96))
    compare.paste(actual_circle, (1024, 96))
    draw = ImageDraw.Draw(compare)
    font = ImageFont.truetype(str(ROOT / "app/src/main/res/font/jost.ttf"), 44)
    draw.text((4, 24), "SOURCE  ·  VECTOR XML", font=font, fill=(10, 10, 10))
    draw.text((1028, 24), "LAUNCHER  ·  INSTALLED APK", font=font, fill=(142, 142, 142))
    compare.save(TOOLS / "icon_source_vs_actual.png")
    compare.save(TOOLS / "icon_compare.png")

    print("wrote", source_svg.relative_to(ROOT), source_png.relative_to(ROOT))
    print("wrote tools/icon_preview.png and tools/icon_source_vs_actual.png")


if __name__ == "__main__":
    sys.exit(main())
