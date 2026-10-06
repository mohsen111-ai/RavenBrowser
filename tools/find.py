"""Prints the centre of the first on-screen element whose text or description contains the query.

A query starting with "=" must match the whole text or description instead, and one starting with "^"
must begin it (case is ignored). A leading "!" picks the last such element on screen instead of the first,
e.g. "!=New tab" for the button at the bottom of the Tabs screen rather than a blank tab's piece.
"""
import re
import sys
import xml.etree.ElementTree as ET

query = sys.argv[2].lower()
last = query.startswith("!")
if last:
    query = query[1:]
mode = query[:1] if query[:1] in "=^" else ""
if mode:
    query = query[1:]
found = None
for node in ET.parse(sys.argv[1]).iter("node"):
    text, desc = node.get("text", "").lower(), node.get("content-desc", "").lower()
    if mode == "=":
        hit = query in (text, desc)
    elif mode == "^":
        hit = text.startswith(query) or desc.startswith(query)
    else:
        hit = query in text + " " + desc
    if hit:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
        if x2 > x1 and y2 > y1:
            found = ((x1 + x2) // 2, (y1 + y2) // 2)
            if not last:
                break
if found is None:
    sys.exit(1)
print(*found)
