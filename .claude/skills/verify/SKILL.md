---
name: verify
description: Drive a headless dev server to verify BearMetalCarts changes at the console surface.
---

# Verifying BearMetalCarts changes

Build: `./gradlew build`. Datagen: `./gradlew runDatagen` (commit the diff under `src/main/generated/`).

## Headless server session

```bash
SP=<scratch dir>
mkfifo $SP/server-in
(setsid sleep 100000 > $SP/server-in < /dev/null &)   # writer keeps FIFO open; without it the server gets EOF on stdin and stops immediately
./gradlew runServer --console=plain < $SP/server-in > $SP/server.log 2>&1 &   # run in background
# wait for "Done (" in server.log, then:
echo "<console command>" > $SP/server-in
```

Gotchas:

- Set `pause-when-empty-seconds=-1` in `run/server.properties` first or nothing ticks without players; restore to 60 after.
- Never `rm`/recreate the FIFO while a server is attached — the live server keeps the old inode and the path stops reaching it. If a second launch fails with `session.lock: already locked`, the first server is still alive: `pgrep -fa devlaunchinjector` and kill it (plus its gradlew wrapper).
- `/fill`, `/setblock`, `/summon` need loaded chunks: `forceload add 990 -10 1130 10` covers the test track; `forceload remove all` when done.
- Powered-rail test track in the dev world: x=1000–1121, y=100, z=0 (unpowered brake segment ~x=1081–1089).

## Flows worth driving

- Speed check: summon two tagged carts at x=1001 y=100.1 z=0.5, set one's speed (`bmc @e[tag=fast] maxspeed 1.2`), `data modify entity ... Motion[0] set value 0.4d` on both, compare `Pos[0]` a few seconds later (vanilla ≈ 0.4 b/t, modded ≈ its max_speed).
- Cart data lives at entity NBT `BearMetalCarts.max_speed` — `/data get|modify entity` round-trips it; `/bmc <sel> maxspeed [<double>]` reads/writes it.
- Persistence: set a speed, `stop`, relaunch, read it back.
- Placement path (MinecartItemMixin) needs a real player placing an item with the `minecraft:custom_data` component — not coverable headless; use `runClient` or a real client.
