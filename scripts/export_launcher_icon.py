#!/usr/bin/env python3
"""Deterministic Android exports of the owner's supplied vault artwork; no redraw."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
BRANDING = ROOT / 'docs/branding'
BACKGROUND = '#10151D'
CROP = (95, 48, 1159, 1032)  # Original 1254px PNG; lettering begins below this crop.
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def export(source):
    BRANDING.mkdir(parents=True, exist_ok=True)
    original = Image.open(source).convert('RGB')
    assert original.size == (1254, 1254), 'Unexpected source: do not guess a crop'
    crop = original.crop(CROP)
    # Canonical crop has sufficient resolution for every exported foreground.
    master = crop.resize((576, round(576 * crop.height / crop.width)), Image.Resampling.LANCZOS)
    master.save(BRANDING / 'private-gallery-vault-master.png', optimize=True)
    render(master)
    (BRANDING / 'source.json').write_text(json.dumps({
        'owner_file': 'Private Gallery Vault Icon.png',
        'library_file_id': 'libfile_d8e4e910b8748191857c905801db1230',
        'original_sha256': hashlib.sha256(Path(source).read_bytes()).hexdigest(),
        'original_size': list(original.size), 'crop': list(CROP),
        'foreground_width_dp': 56, 'canvas_dp': 108, 'safe_circle_diameter_dp': 66,
        'background': BACKGROUND, 'method': 'Crop and proportional Lanczos resampling of owner pixels only; no generated replacement.',
    }, indent=2) + '\n')


def foreground(master, scale):
    canvas = Image.new('RGBA', (round(108 * scale), round(108 * scale)))
    width = round(56 * scale)
    artwork = master.resize((width, round(width * master.height / master.width)), Image.Resampling.LANCZOS).convert('RGBA')
    # Fade only the crop's outer background margin, outside the vault outline.
    alpha = Image.new('L', artwork.size)
    ImageDraw.Draw(alpha).rectangle((1, 1, artwork.width-2, artwork.height-2), fill=255)
    alpha = alpha.filter(ImageFilter.GaussianBlur(max(.5, scale * .4)))
    artwork.putalpha(alpha)
    canvas.alpha_composite(artwork, ((canvas.width-artwork.width)//2, (canvas.height-artwork.height)//2))
    return canvas


def visible(master, size):
    scale = size / 72
    layer = foreground(master, scale)
    composite = Image.new('RGBA', layer.size, BACKGROUND)
    composite.alpha_composite(layer)
    edge = round(18 * scale)
    return composite.crop((edge, edge, edge+size, edge+size))


def render(master):
    for density, scale in DENSITIES.items():
        folder = RES / ('drawable-' + density)
        folder.mkdir(parents=True, exist_ok=True)
        foreground(master, scale).save(folder / 'private_gallery_launcher_artwork.png', optimize=True)
        folder = RES / ('mipmap-' + density)
        folder.mkdir(parents=True, exist_ok=True)
        size = round(48 * scale)
        square = visible(master, size)
        square.save(folder / 'ic_launcher.png', optimize=True)
        mask = Image.new('L', square.size)
        ImageDraw.Draw(mask).ellipse((0, 0, size-1, size-1), fill=255)
        square.putalpha(mask)
        square.save(folder / 'ic_launcher_round.png', optimize=True)
    preview = Image.new('RGB', (900, 350), '#242830')
    for index, (name, radius) in enumerate([('Circle', 150), ('Samsung-style squircle', 85), ('Rounded square', 50)]):
        icon = visible(master, 260)
        mask = Image.new('L', icon.size)
        draw = ImageDraw.Draw(mask)
        if name == 'Circle': draw.ellipse((0, 0, 259, 259), fill=255)
        else: draw.rounded_rectangle((0, 0, 259, 259), radius=radius, fill=255)
        icon.putalpha(mask)
        preview.paste(icon, (20 + index*300, 20), icon)
        ImageDraw.Draw(preview).text((20 + index*300, 300), name, fill='white')
    preview.save(BRANDING / 'launcher-mask-preview.png', optimize=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path)
    args = parser.parse_args()
    if args.source: export(args.source)
    else: render(Image.open(BRANDING / 'private-gallery-vault-master.png').convert('RGB'))
