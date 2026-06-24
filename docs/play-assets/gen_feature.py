#!/usr/bin/env python3
"""Generate MEAT REC Play Store feature graphic (1024x500)."""
from PIL import Image, ImageDraw, ImageFont
import math

W, H = 1024, 500
CHARCOAL = (12, 12, 16)
YELLOW = (255, 199, 44)
ORANGE = (250, 70, 22)
WHITE = (255, 255, 255)
MUTED = (136, 146, 164)

img = Image.new("RGB", (W, H), CHARCOAL)
d = ImageDraw.Draw(img)

# Background gradient: charcoal → slightly warm at right
for x in range(W):
    t = x / W
    r = int(12 + t * 18)
    g = int(12 + t * 8)
    b = int(16 + t * 6)
    d.line([(x, 0), (x, H)], fill=(r, g, b))

# Large faint waveform strip across full width (background motif)
mid = H // 2
random_seed = [0.2, 0.5, 0.35, 0.7, 0.45, 0.9, 0.6, 1.0, 0.55, 0.8, 0.4, 0.65,
               0.3, 0.75, 0.5, 0.85, 0.45, 0.95, 0.6, 0.7, 0.35, 0.55, 0.25, 0.6,
               0.4, 0.8, 0.5, 0.9, 0.45, 0.7]
bw = 14
gap = 20
x = 40
i = 0
while x < W - 40:
    frac = random_seed[i % len(random_seed)]
    h = int(150 * frac)
    # fade bars near the text area (left third) so text stays readable
    alpha = 1.0
    if x < 520:
        alpha = 0.18
    col = (
        int(CHARCOAL[0] + (ORANGE[0] - CHARCOAL[0]) * frac * alpha),
        int(CHARCOAL[1] + (ORANGE[1] - CHARCOAL[1]) * frac * alpha),
        int(CHARCOAL[2] + (ORANGE[2] - CHARCOAL[2]) * frac * alpha),
    )
    d.rounded_rectangle([x, mid - h, x + bw, mid + h], radius=bw // 2, fill=col)
    x += bw + gap
    i += 1

# Recording dot
d.ellipse([60, 150, 110, 200], fill=ORANGE)
try:
    rec_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 26)
except Exception:
    rec_font = ImageFont.load_default()
d.text((125, 158), "REC", font=rec_font, fill=ORANGE)

# Main wordmark
try:
    title_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 96)
    sub_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 30)
except Exception:
    title_font = ImageFont.load_default()
    sub_font = ImageFont.load_default()

d.text((58, 215), "MEAT REC", font=title_font, fill=YELLOW)
d.text((62, 330), "Professional field recorder", font=sub_font, fill=WHITE)
d.text((62, 372), "EQ · Trim · Pitch shift · Transcribe · Drive backup", font=sub_font, fill=MUTED)

img.save("/Users/meatball_mac/RECORDER_PROJECT/docs/play-assets/feature-1024x500.png", "PNG")
print("feature-1024x500.png saved", img.size)
