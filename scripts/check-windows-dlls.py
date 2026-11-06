#!/usr/bin/env python3
"""Fails when an executable or DLL in a Windows app image needs a DLL that is neither in the image
nor part of Windows itself.

GitHub's Windows runners have the Visual C++ runtime installed system-wide, so a smoke test there
never misses it; a clean Windows does. 0.2.0's MSI shipped no vcruntime140.dll, vcruntime140_1.dll
or msvcp140.dll, and its Mirada.exe failed to start with STATUS_DLL_NOT_FOUND (0xC0000135) in
winget's validation. This reads every PE file's import table (and its delay-load table) instead of
running anything, so it catches what the runner would have hidden, on any platform.

Usage: check-windows-dlls.py <app-image-dir>
"""

import os
import struct
import sys

# DLLs every Windows 10 and 11 has. A name not here and not in the image fails the check: add one
# only when it really ships with Windows (not with a redistributable).
SYSTEM = {
    "advapi32.dll", "bcrypt.dll", "comctl32.dll", "comdlg32.dll", "crypt32.dll", "d2d1.dll",
    "d3d11.dll", "d3d9.dll", "dbghelp.dll", "dwmapi.dll", "dwrite.dll", "dxgi.dll", "gdi32.dll",
    "gdiplus.dll", "imm32.dll", "iphlpapi.dll", "kernel32.dll", "msimg32.dll", "msvcrt.dll",
    "mswsock.dll", "ncrypt.dll", "netapi32.dll", "ntdll.dll", "ole32.dll", "oleaut32.dll",
    "powrprof.dll", "psapi.dll", "rpcrt4.dll", "secur32.dll", "setupapi.dll", "shcore.dll",
    "shell32.dll", "shlwapi.dll", "ucrtbase.dll", "uiautomationcore.dll", "urlmon.dll",
    "user32.dll", "userenv.dll", "uxtheme.dll", "version.dll", "winhttp.dll", "wininet.dll",
    "winmm.dll", "winspool.drv", "ws2_32.dll", "wtsapi32.dll", "windowscodecs.dll",
}


def is_system(name):
    # The universal C runtime's API sets are part of Windows 10 and later.
    return name in SYSTEM or name.startswith("api-ms-win-") or name.startswith("ext-ms-")


def imports(path):
    """The DLL names a PE file imports, eagerly or delay-loaded, lower-cased; None if not a PE file."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:2] != b"MZ" or len(data) < 0x40:
        return None
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    if data[pe:pe + 4] != b"PE\0\0":
        return None
    sections, optional_size = struct.unpack_from("<H12xH", data, pe + 6)
    optional = pe + 24
    magic = struct.unpack_from("<H", data, optional)[0]
    if magic == 0x20B:  # PE32+
        image_base = struct.unpack_from("<Q", data, optional + 24)[0]
        directories = optional + 112
    else:
        image_base = struct.unpack_from("<I", data, optional + 28)[0]
        directories = optional + 96
    table = optional + optional_size
    spans = [struct.unpack_from("<8xIIII", data, table + 40 * i) for i in range(sections)]

    def offset(rva):
        for size, address, raw_size, raw in spans:
            if address <= rva < address + max(size, raw_size):
                return raw + rva - address
        raise ValueError(f"{path}: address {rva:#x} is in no section")

    def name_at(rva):
        start = offset(rva)
        return data[start:data.index(b"\0", start)].decode("ascii").lower()

    names = []
    rva = struct.unpack_from("<I", data, directories + 8 * 1)[0]
    if rva:
        at = offset(rva)
        while True:
            entry = struct.unpack_from("<5I", data, at)
            if not any(entry):
                break
            names.append(name_at(entry[3]))
            at += 20
    rva = struct.unpack_from("<I", data, directories + 8 * 13)[0]
    if rva:
        at = offset(rva)
        while True:
            attributes, name = struct.unpack_from("<II", data, at)
            if not name:
                break
            # Attribute bit 0 says the fields are RVAs; old linkers wrote addresses.
            names.append(name_at(name if attributes & 1 else name - image_base))
            at += 32
    return names


def main():
    root = sys.argv[1]
    files = {}
    for folder, _, entries in os.walk(root):
        for entry in entries:
            if entry.lower().endswith((".exe", ".dll")):
                files[entry.lower()] = os.path.join(folder, entry)
    if not any(name.endswith(".exe") for name in files):
        sys.exit(f"no executable in {root}")
    failures = 0
    for name, path in sorted(files.items()):
        needed = imports(path)
        if needed is None:
            continue
        for dll in needed:
            if dll not in files and not is_system(dll):
                print(f"::error::{os.path.relpath(path, root)} needs {dll}, which is neither in the image nor part of Windows")
                failures += 1
    if failures:
        sys.exit(f"{failures} missing DLL(s)")
    print(f"Windows DLLs: all {len(files)} files' imports are in the image or in Windows")


if __name__ == "__main__":
    main()
