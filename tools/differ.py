"""Prints "yes" if two screenshots (PNG) differ in the middle of the screen (30% to 80% down), else "no".

The status bar's clock changes between two screenshots too, so only the middle counts: a live wallpaper moving there.
"""
import struct
import sys
import zlib


def rows(path):
    data = open(path, "rb").read()
    i, idat, height = 8, b"", 0
    while i < len(data):
        n, kind = struct.unpack(">I4s", data[i:i + 8])
        chunk = data[i + 8:i + 8 + n]
        if kind == b"IHDR":
            height = struct.unpack(">II", chunk[:8])[1]
        if kind == b"IDAT":
            idat += chunk
        i += 12 + n
    raw = zlib.decompress(idat)
    stride = len(raw) // height
    return [raw[y * stride:(y + 1) * stride] for y in range(height)]


a, b = rows(sys.argv[1]), rows(sys.argv[2])
h = min(len(a), len(b))
print("yes" if any(a[y] != b[y] for y in range(h * 30 // 100, h * 80 // 100)) else "no")
