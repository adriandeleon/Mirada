# Writes a HEIC laid out as an iPhone lays out its photos, through libheif's encoder (ctypes):
# a grid of HEVC tiles whose last column and row are cut off (an iPhone's 4032 × 3024 is 8 × 6
# tiles of 512, cropped), turned by an irot box as well as tagged with EXIF orientation 6, and with
# the colour profile on each tile rather than on the grid. For scripts/make-format-matrix.sh,
# since neither ImageMagick nor heif-enc here writes a grid.
#
# The picture, upright, is 48 × 32: red on the left half, blue on the right. Stored, it is that
# turned a quarter anticlockwise, 32 × 48 in 20-pixel tiles (2 × 3, cropped from 40 × 60), and
# irot turns it back: a reader that also applies the EXIF orientation turns it twice.
#
# Usage: write-heic-grid.py <profile.icc> <out.heic>
import ctypes, sys

profile_path, out = sys.argv[1], sys.argv[2]
heif = ctypes.cdll.LoadLibrary("libheif.so.1")
c_void_p, c_int, c_char_p = ctypes.c_void_p, ctypes.c_int, ctypes.c_char_p


class Error(ctypes.Structure):
    _fields_ = [("code", c_int), ("subcode", c_int), ("message", c_char_p)]


def check(error):
    if error.code != 0:
        sys.exit(f"libheif: {error.message.decode()}")


for name in ("heif_context_get_encoder_for_format", "heif_image_create", "heif_image_add_plane",
             "heif_image_set_raw_color_profile", "heif_context_add_grid_image", "heif_context_add_image_tile",
             "heif_context_add_exif_metadata", "heif_context_write_to_file", "heif_encoder_set_lossy_quality",
             "heif_context_set_primary_image"):
    getattr(heif, name).restype = Error
heif.heif_context_alloc.restype = c_void_p
heif.heif_encoding_options_alloc.restype = c_void_p
heif.heif_image_get_plane.restype = ctypes.POINTER(ctypes.c_uint8)
heif.heif_image_get_plane.argtypes = [c_void_p, c_int, ctypes.POINTER(c_int)]

TILE, COLUMNS, ROWS = 20, 2, 3
WIDTH, HEIGHT = 32, 48  # stored; upright it is 48 × 32
CHROMA_INTERLEAVED_RGB, COLORSPACE_RGB, CHANNEL_INTERLEAVED = 10, 1, 10

ctx = c_void_p(heif.heif_context_alloc())
encoder = c_void_p()
check(heif.heif_context_get_encoder_for_format(ctx, 1, ctypes.byref(encoder)))  # 1: HEVC
check(heif.heif_encoder_set_lossy_quality(encoder, 90))
profile = open(profile_path, "rb").read()

# heif_encoding_options, version 5 on: image_orientation (an int enum) sits at byte 20 on a 64-bit
# machine, after four bytes, a pointer and a byte. 6: "turn 90° clockwise to view", as in EXIF.
options = c_void_p(heif.heif_encoding_options_alloc())
assert ctypes.c_uint8.from_address(options.value).value >= 5, "libheif too old for image_orientation"
ctypes.c_int.from_address(options.value + 20).value = 6
grid = c_void_p()
check(heif.heif_context_add_grid_image(ctx, WIDTH, HEIGHT, COLUMNS, ROWS, options, ctypes.byref(grid)))
for row in range(ROWS):
    for column in range(COLUMNS):
        image = c_void_p()
        check(heif.heif_image_create(TILE, TILE, COLORSPACE_RGB, CHROMA_INTERLEAVED_RGB, ctypes.byref(image)))
        check(heif.heif_image_add_plane(image, CHANNEL_INTERLEAVED, TILE, TILE, 8))
        stride = c_int()
        plane = heif.heif_image_get_plane(image, CHANNEL_INTERLEAVED, ctypes.byref(stride))
        for y in range(TILE):
            for x in range(TILE):
                # Upright, red left and blue right; stored turned anticlockwise, so the upright
                # picture's left half is the stored bottom half.
                red = (row * TILE + y) >= HEIGHT // 2
                at = y * stride.value + 3 * x
                for i, value in enumerate((0xD0, 0x30, 0x2A) if red else (0x2A, 0x5F, 0xD0)):
                    plane[at + i] = value
        check(heif.heif_image_set_raw_color_profile(image, b"prof", profile, len(profile)))
        check(heif.heif_context_add_image_tile(ctx, grid, column, row, image, encoder))

check(heif.heif_context_set_primary_image(ctx, grid))
# EXIF orientation 6 (turn clockwise to view), big-endian, IFD0 with one entry.
exif = bytes.fromhex("4d4d002a00000008" "0001" "011200030000000100060000" "00000000")
check(heif.heif_context_add_exif_metadata(ctx, grid, exif, len(exif)))
check(heif.heif_context_write_to_file(ctx, out.encode()))
data = open(out, "rb").read()
assert b"irot" in data, "libheif wrote no irot for the grid"
assert b"grid" in data
