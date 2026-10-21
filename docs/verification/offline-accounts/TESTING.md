# Current build verification

Source commit: 4d3b5421eee487f15c26aebba80f9fe70e2b4836

Only the statuses below apply to this build. Native GUI inspection and rendered gameplay are not inferred from compilation or process liveness.

## BACKEND-SMOKE

### SQLite settings persist across backend reopen: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Windows CPU / GPU / RAM / disk information: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Official Mojang manifest API: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Create and isolate a real native profile: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Reject launch when no account is selected: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Create and persist a real local offline account in SQLite: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Offline launch identity resolves without Microsoft tokens or Client ID: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Delete an offline account without touching Microsoft credentials: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Download and install managed Java 21 with SHA256: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Download and install managed Java 8 with SHA256: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Download and install managed Java 17 with SHA256: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Download and install managed Java 25 with SHA256: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real Vanilla 1.21.4 client / libraries / natives / assets installation: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Re-verify real cached Minecraft files: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real Fabric 1.21.4 installation: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real Modrinth search / compatibility / dependency install: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Disable and re-enable actual mod files: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Update real disabled Modrinth project without silently enabling it: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Export and reimport a real Modrinth .mrpack: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Remove mod into safe backup storage: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Resolve and download real Iris required dependencies: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real forge 1.21.4 installation: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real neoforge 1.21.4 installation: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Real quilt 1.21.4 installation: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Migrate real game data with SHA256 and retain original: PASSED

Executed against the real native backend on Windows; no mock APIs or data.

### Licensed Microsoft OAuth / refresh / multi-account: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### Rendered Minecraft game session and actual playtime: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### CurseForge API and ZIP modpack with a real key: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### Discord Desktop Rich Presence: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### RAM in a running licensed Minecraft JVM: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### User-world backup and restoration: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

### Windows GUI restart and interactive installer: NOT TESTED

Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.

## INSTALLER-SMOKE

### Real NSIS silent installation: PASSED

Installed into a new isolated directory. Full payload SHA256=83A3B4FEE3FC7ADD3563674A8F737B2150EFF0EA8D972D8508E0A0413B9C72F3 matches the production binary including Tauri's exact UNK-to-NSS bundle marker transform; no other byte differences are allowed.

### Real NSIS silent uninstallation: PASSED

The installed executable was removed. The app-data preservation probe survived unchanged. This probe is not a Minecraft world.

### Interactive installer, SmartScreen and standard-user desktop flow: NOT TESTED

Silent execution on a disposable Windows runner does not verify the full interactive user experience.

## NATIVE-SMOKE

### Native GUI smoke harness: NOT TESTED

Error: WebView2 CDP was not reachable. The spawned EXE remained running, but GUI smoke tests require a working interactive Windows/WebView2 inspection environment.


