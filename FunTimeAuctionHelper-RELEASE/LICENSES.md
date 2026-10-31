# Licenses and third-party notices

## FunTime Auction Helper Ultra Pro

This mod's own source code (everything under `FunTimeAuctionHelper/`) is released under the
MIT License:

```
MIT License

Copyright (c) 2026 FunTime Auction Helper contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The mod itself is not redistributed inside the JARs in this folder in compiled form of the
third-party components below; they are dependencies you have to install yourself.

## Dependencies of the built JARs

| Component | Version used | License / terms |
|---|---|---|
| Minecraft Java Edition | 1.16.5, 1.21.4, 1.21.11 | Minecraft EULA / Mojang terms — not redistributed here |
| Fabric Loader | 0.20.1 | Apache License 2.0 |
| Fabric API | `0.42.0+1.16`, `0.119.4+1.21.4`, `0.141.6+1.21.11` | Apache License 2.0 |
| Fabric Loom (build only) | 1.19.3 | Apache License 2.0 |
| Mojang official mappings (build only) | matching each version | Mojang mappings license, see the notice below |
| Gradle | 9.8.2 | Apache License 2.0 |
| Gson (`compileOnly`, only used by the standalone JUnit tests) | 2.10.1 | Apache License 2.0 |
| JUnit Jupiter | 5.10.2 | Eclipse Public License 2.0 |

## Mojang official mappings notice

```
(c) 2020 Microsoft Corporation. These mappings are provided "as-is" and you bear the risk
of using them. You may copy and use the mappings for development purposes, but you may not
redistribute the mappings complete and unmodified. Microsoft makes no warranties, express
or implied, with respect to the mappings provided here. Use and modification of this
document or the source code (in any form) of Minecraft: Java Edition is governed by the
Minecraft End User License Agreement available at
https://account.mojang.com/documents/minecraft_eula.
```

The mappings are used at build time only (deobfuscation of the compiled code); the mappings
files themselves are not shipped in the JARs.

## Server rules

The mod performs no anti-cheat bypass, no packet spam, no GUI-click automation and no
client-side automation of the FunTime auction. If FunTime's rules forbid any part of this
mod's behaviour on their server, do not install or use it there — the server rules take
precedence over anything written in this document.
