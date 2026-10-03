"""Generate a small dependency-free Windows ICO for the desktop companion."""
import math
import struct
import zlib
from pathlib import Path

SIZE = 64
GREEN = (36, 122, 86, 255)
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)


def inside_polygon(x, y, points):
    inside = False
    previous = points[-1]
    for current in points:
        x1, y1 = previous
        x2, y2 = current
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
        previous = current
    return inside


def pixel(x, y):
    if 4 <= x < 60 and 4 <= y < 60:
        cx = 8 if x < 8 else 56 if x >= 56 else x
        cy = 8 if y < 8 else 56 if y >= 56 else y
        if math.hypot(x - cx, y - cy) <= 4:
            box = [(17, 25), (32, 17), (47, 25), (47, 42), (32, 50), (17, 42)]
            if inside_polygon(x + 0.5, y + 0.5, box):
                if y < 26 or y > 42 or (30 <= x < 34 and y >= 25):
                    return GREEN
                return WHITE
            return GREEN
    return TRANSPARENT


def png_bytes():
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            raw.extend(pixel(x, y))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


image = png_bytes()
entry = struct.pack("<BBBBHHII", SIZE, SIZE, 0, 0, 1, 32, len(image), 22)
Path(__file__).with_name("inventory.ico").write_bytes(struct.pack("<HHH", 0, 1, 1) + entry + image)
print("Created desktop/inventory.ico")
