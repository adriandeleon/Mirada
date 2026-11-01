# Encodes raw 8-bit RGB or RGBA pixels as a lossless JPEG XL with libjxl's encoder, through ctypes.
# For scripts/make-format-matrix.sh, since ImageMagick here cannot write JPEG XL.
# Usage: write-jxl.py <raw-file> <width> <height> <channels 3|4> <out.jxl>
import ctypes, sys
raw, width, height, channels, out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
for name in ("libjxl.so.0.12", "libjxl.so.0.11", "libjxl.so"):
    try:
        jxl = ctypes.cdll.LoadLibrary(name); break
    except OSError:
        continue
c_void_p, c_size_t = ctypes.c_void_p, ctypes.c_size_t
jxl.JxlEncoderCreate.restype = c_void_p
jxl.JxlEncoderFrameSettingsCreate.restype = c_void_p
jxl.JxlEncoderFrameSettingsCreate.argtypes = [c_void_p, c_void_p]
enc = c_void_p(jxl.JxlEncoderCreate(None))
info = ctypes.create_string_buffer(512)
jxl.JxlEncoderInitBasicInfo(info)
ctypes.c_uint32.from_buffer(info, 4).value = width
ctypes.c_uint32.from_buffer(info, 8).value = height
ctypes.c_uint32.from_buffer(info, 12).value = 8          # bits_per_sample
ctypes.c_int32.from_buffer(info, 36).value = 1           # uses_original_profile (lossless)
if channels == 4:
    ctypes.c_uint32.from_buffer(info, 56).value = 1      # num_extra_channels
    ctypes.c_uint32.from_buffer(info, 60).value = 8      # alpha_bits
assert jxl.JxlEncoderSetBasicInfo(enc, info) == 0
color = ctypes.create_string_buffer(256)
jxl.JxlColorEncodingSetToSRGB(color, 0)
assert jxl.JxlEncoderSetColorEncoding(enc, color) == 0
settings = c_void_p(jxl.JxlEncoderFrameSettingsCreate(enc, None))
assert jxl.JxlEncoderSetFrameLossless(settings, 1) == 0
fmt = (ctypes.c_uint32 * 6)(channels, 2, 0, 0, 0, 0)
pixels = open(raw, "rb").read()
assert jxl.JxlEncoderAddImageFrame(settings, fmt, pixels, c_size_t(len(pixels))) == 0
jxl.JxlEncoderCloseInput(enc)
size = 1 << 20
buffer = ctypes.create_string_buffer(size)
nxt = ctypes.c_void_p(ctypes.addressof(buffer)); avail = c_size_t(size)
assert jxl.JxlEncoderProcessOutput(enc, ctypes.byref(nxt), ctypes.byref(avail)) == 0
open(out, "wb").write(buffer.raw[:size - avail.value])
