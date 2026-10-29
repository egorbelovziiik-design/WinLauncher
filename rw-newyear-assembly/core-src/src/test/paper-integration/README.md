# Paper 1.21.4 integration test

Runs a disposable, loopback-only Paper 1.21.4 server and protocol client. It
checks old `item_model` migration on player join, ender chest, container open
and item pickup; preserves charges/CMD; tests fresh items, `/mega`, MEGA buff,
the official old-pack upgrade and the actual resource-pack packet. This does
**not** render a Minecraft client or verify visuals.

Requirements: Java 21, `javac`, Node.js and `minecraft-protocol@1.62.0` in a
local `node_modules`, a Paper 1.21.4 paperclip JAR, its matching Mojang vanilla
server JAR, and a Spigot 1.21 API JAR with compile dependencies. The runner
verifies the vanilla JAR's SHA-256 against the Paper download context and never
downloads or changes the supplied JARs. It creates all server data in a
`TemporaryDirectory` and removes it on exit. Review the Minecraft EULA before
explicitly accepting it for this temporary server.

```sh
python3 src/test/paper-integration/run.py \
  --paper /path/to/paper-1.21.4.jar --vanilla /path/to/mojang-1.21.4.jar \
  --api-classpath '/path/to/spigot-api.jar:/path/to/dependencies/*' \
  --node-modules /path/to/node_modules --accept-eula
```

Pass `--core`, `--java`, `--javac` or `--node` to override the defaults. The
core JAR under test must already have been built from the intended sources.
The isolated server uses offline mode exclusively for this local test; do not
copy its generated settings to a live server.
