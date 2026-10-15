# Easy Building — Fabric 26.2

Port of [Easy Building](https://github.com/marianolarre/easy-building-minecraft-mod) (by **Marum**) from Fabric 26.1.2 to **Minecraft 26.2**.
Client-side only. Pick a vertical or horizontal plane with **Left Alt** while looking at a block, then hold the use key and drag
to place blocks fast and straight (walls, floors, bridges, roofs). Respects slabs and stairs.

All credit for the mod and its design goes to Marum. This folder only adapts the 26.1.2 code to the 26.2 APIs.

## What changed from 26.1.2

| File | Change |
| --- | --- |
| `gradle.properties` | Minecraft `26.2`, Fabric Loader `0.19.5`, Fabric API `0.160.0+26.2`, JDK 25 pinned. |
| `fabric.mod.json` | `minecraft: >=26.2- <26.3-`; real name/description/authors instead of the template text; `environment: client`. |
| `EasyBuildingClient` | Fabric API's `LevelRenderContext` no longer exposes `bufferSource()`: the grid is now submitted with `submitNodeCollector().submitCustomGeometry(...)` from `LevelRenderEvents.COLLECT_SUBMITS`. |
| `EasyBuildingClient` | `BlockPos.getCenter()` is gone: replaced by `Vec3.atCenterOf(pos)` (2 places). |
| `EasyBuildingClient` | Null guard in the render callback: `getProjectedBlock()` returns `null` when the view ray is parallel to the plane (it crashed the render before). |
| `EasyBuildingClient` | **Crash when leaving the world with the mode on** (found 2026-10-15 in a real profile): the state is static and the tick handler called `getProjectedBlock()` with `client.player == null`. Now the state is cleared on `ClientPlayConnectionEvents.DISCONNECT`, the tick does nothing without a world (and drops pending key presses), and `getProjectedBlock()` is null-safe. |

The placement mixin (`MultiPlayerGameMode.useItemOn`) is unchanged: the method signature is identical in 26.2.

## Build and test

```
.\gradlew.bat build --offline                 # jar in build/libs
.\gradlew.bat runClientGameTest --offline     # real client: Alt, hold use key, checks blocks on the server, screenshots
```

`runClientGameTest` ends in `BUILD FAILED` because of the shutdown watchdog; read the `[EB-TEST]` lines
(`[EB-TEST] ALL OK` = passed). Screenshots go to `build/run/clientGameTest/screenshots`.

## License

The repository ships a CC0 `LICENSE` (Fabric template); the Modrinth page lists MIT. Both allow this port.
