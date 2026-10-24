#!/usr/bin/env bash
# Cross compiles NexusInjector.exe for Windows.
#
# Requirements: only Zig (no Windows machine needed).
#   pip install ziglang          # or grab a release from https://ziglang.org/download/
#
set -e
cd "$(dirname "$0")"

ZIG="${ZIG:-zig}"
if ! command -v "$ZIG" >/dev/null 2>&1 && [ -x "$(python3 -c 'import os,sys;print(os.path.join(sys.prefix,"bin","python-zig"))' 2>/dev/null)" ]; then
  ZIG="$(python3 -c 'import os,sys;print(os.path.join(sys.prefix,"bin","python-zig"))')"
fi

mkdir -p build
"$ZIG" cc -target x86_64-windows-gnu -O2 -std=c11 \
  -Wall -Wno-unused-parameter \
  -o build/NexusInjector.exe src/nexus_injector.c \
  -Wl,--subsystem,windows -lgdi32 -luser32 -lcomctl32 -lcomdlg32 \
  -lshell32 -lole32 -lurlmon -lshlwapi -ladvapi32 -luxtheme

echo "built: $(pwd)/build/NexusInjector.exe"
