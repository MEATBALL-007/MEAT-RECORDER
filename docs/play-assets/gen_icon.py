#!/usr/bin/env python3
"""Generate MEAT REC Play Store hi-res icon (512x512)."""
from PIL import Image, ImageDraw, ImageFont
import math

S = 512
CHARCOAL = (12, 12, 16)
CARD = (22, 22, 24)
YELLOW = (255, 199, 44)
ORANGE = (250, 70, 22)
WHITE = (255, 255, 255)

img = Image.new("RGB", (S, S), CHARCOAL)
d = ImageDraw.Draw(img)

# Subtle radial-ish background: draw concentric rounded rects darker→lighter for depth
for i, c in enumerate([(8, 8, 11), (10, 10, 14), (12, 12, 16)]):
    inset = i * 6
    d.rounded_rectangle([inset, inset, S - inset, S - inset], radius=110 - i * 4, fill=c)

# Center waveform bars (the brand motif) — symmetric, mic-like column
cx, cy = S // 2, S // 2
bar_w = 26
gap = 18
# Heights pattern (fraction of half-height) — like an audio level / EQ
pattern = [0.30, 0.55, 0.80, 1.0, 0.80, 0.55, 0.30]
n = len(pattern)
total_w = n * bar_w + (n - 1) * gap
start_x = cx - total_w // 2
max_h = 150

for i, frac in enumerate(pattern):
    x0 = start_x + i * (bar_w + gap)
    h = int(max_h * frac)
    # color gradient center=orange, edges=yellow
    t = abs(i - n // 2) / (n // 2)
    col = (
        int(ORANGE[0] * (1 - t) + YELLOW[0] * t),
        int(ORANGE[1] * (1 - t) + YELLOW[1] * t),
        int(ORANGE[2] * (1 - t) + YELLOW[2] * t),
    )
    d.rounded_rectangle([x0, cy - h, x0 + bar_w, cy + h], radius=bar_w // 2, fill=col)

# "REC" dot top-right of cluster — a recording indicator
dot_r = 30
d.ellipse([S - 130, 70, S - 130 + dot_r * 2, 70 + dot_r * 2], fill=ORANGE)

# Bottom wordmark "MEAT REC"
try:
    font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 58)
except Exception:
    font = ImageFont.load_default()
text = "MEAT REC"
tb = d.textbbox((0, 0), text, font=font)
tw = tb[2] - tb[0]
d.text(((S - tw) // 2, S - 120), text, font=font, fill=YELLOW)

img.save("/Users/meatball_mac/RECORDER_PROJECT/docs/play-assets/icon-512.png", "PNG")
print("icon-512.png saved", img.size)
