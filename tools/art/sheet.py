# A contact sheet of rendered wallpapers, for looking at several at once.
import sys
from PIL import Image
files = sys.argv[2:]
cols = 5
w, h = 216, 467
rows = (len(files) + cols - 1) // cols
out = Image.new("RGB", (w * cols, h * rows), "black")
for i, f in enumerate(files):
    out.paste(Image.open(f).convert("RGB").resize((w, h)), ((i % cols) * w, (i // cols) * h))
out.save(sys.argv[1])
