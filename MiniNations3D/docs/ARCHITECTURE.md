# Architecture

## Client / Server split

[Client] Main.client → CameraController, MapController, BuildingController, HUD, MainMenu
  └─> RemoteEvents → [Server] Main.server → EconomyService etc → FireAllClients

All important logic (coins, resources, wars) server-authoritative.

## DataStores

- MiniNations_Profiles_v1 : p_<userId> -> PlayerProfile
- MiniNations_Sessions_v1 : s_<userId>_<time> -> lastSession
- MiniNations_Maps_v1 : map_<userId>_<name> -> MapData (JSON < 3.8 MB)

## Remotes

Protected by rate limit 8/s per player. Server validates owner etc.

## Map generation

ProvinceData ~120 provinces procedural. MapController creates Parts with ClickDetectors.
Ocean part, mountains (higher), forests (trees). ProvinceOwnerChanged recolors via Tween.

