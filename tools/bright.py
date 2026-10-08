"""Prints how bright the middle of a raw screenshot is (adb exec-out screencap, without -p): 0 is black, 255 white.

Only the band from 30% to 80% down counts, where the page is, away from the bars. A raw screenshot is a small header
(width, height, format and, on newer Android, a colour space) followed by 4 bytes a pixel.
"""
import struct
import sys

data = open(sys.argv[1], "rb").read()
w, h = struct.unpack("<II", data[:8])
pixels = data[len(data) - w * h * 4:]
total = n = 0
for y in range(h * 30 // 100, h * 80 // 100, 8):
    row = pixels[y * w * 4:(y + 1) * w * 4]
    for x in range(0, w * 4, 32):
        total += row[x] + row[x + 1] + row[x + 2]
        n += 3
print(total // max(n, 1))
