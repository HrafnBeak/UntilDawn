# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

- `UntilDawn/` — the project: a Fabric mod (Minecraft 26.3, Java 25, Mojang names) for a tower-defence game. A player places a **Core**; each night a wave of custom mobs, spawned as squads, comes for it.
- `Baritone/` — a fork of Baritone with entity pathing. It is an **optional** dependency, compiled against through `baritone_jar` in `UntilDawn/gradle.properties` (build it with `gradlew :fabric:build` inside `Baritone/`).
- `UntilDawn/pathing-backup/` and `UntilDawn/bin/` are stale copies of older code. They are not built, so don't edit them or treat them as current.

## Commands (run in `UntilDawn/`)

- `./gradlew build` — compiles and runs `check`, which includes the server game tests. Add `-x runGameTest` to skip them.
- `./gradlew runGameTest` — headless server game tests (`src/gametest/.../UntilDawnGameTests.java`). There is no per-test filter; the whole batch runs, and passing tests log `<name> passed on tick N`.
- `./gradlew runClientGameTest` — opens a game window. It is needed for anything that uses Baritone (`UntilDawnClientGameTests.java`), because Baritone only plans inside a client.
- `./gradlew runClient` / `runServer` — play-test.
- There is no linter. Fast compile check: `./gradlew compileJava compileGametestJava`.

Game tests use the 16×16 `untildawn-test:arena` structure, which is just a stone floor at y=0, so mobs stand at y=1. Scenario tests spawn mobs, give them a target (usually a no-AI cow), and pass when it gets hurt.

## Architecture

**Game loop** — `game/WaveManager` (server tick) handles the Core's lifecycle, nights and waves, spawning squads, glowing or ending the last few mobs, and the boss bar. World state lives in `game/CoreState`, which is `SavedData` with a codec; new fields need `optionalFieldOf` so old saves still load. While a Core is active, vanilla natural spawning is blocked.

**Mobs** — every wave mob implements `entity/WaveMob`: `pathAbilities()`, `settings()` (its `UntilDawnConfig.MobSettings`) and a lazily created `MobPathController`. `Mob`'s constructor calls `registerGoals()` before field initialisers run, which is why the controller is created lazily. Zombie types drop their vanilla movement goals (`WaveMobs.removeVanillaMovement`) and use `StationaryMeleeGoal`, so **all movement goes through `SquadObjectiveGoal` → `MobPathController`**. The wave spiders (`Webber`, `SneakingSpider`, set up in `WaveSpiders`) do the same but keep vanilla's leap at a close target. There are a few exceptions:
- The Sneaking Spider drives the controller from `SneakGoal` while it is still invisible.
- The `Assassin` drives the controller from its own `AssassinGoal`.
- The Assassin is a loner (`WaveMob.joinsSquads()` is false), so it is never in a squad.

The specialists are defined by `PathAbilities`:
- Miner: `canBreak`. On a dig job it digs any of the job's blocks within reach of where it stands (`digInReach`), so it needn't take the asker's spot, and it takes jobs while hanging on a ladder
- Bomber: `canBreak` and `explosive`. It does its own blast (`BomberCreeper.blast`, not vanilla's, which needs mob griefing): `BlockDamage.blast` damages every block in the radius, most at the centre (`bomber.blockDamage`, in ticks of Miner digging), so weak walls fall and strong ones only crack
- Builder: `canPlace` (Unstable Planks and Unstable Ladders, which crumble away after the night: `UnstableDecay`, on scheduled ticks, faster as the next night nears and much faster while the Core screen skips to night, certain just before it; `PathStep.ladders()` says which of a step's `places()` are ladders). It can also break those two blocks itself, in `builder.breakTicks` (`BlockView.isUnstable`, `breaksItself`). Builders answer squadmates' build requests even mid-fight, and from a ladder (not in mid-air; `MobPathController.isFree()`), placing what is within reach from where they are (`buildInReach`). Waiting for a Miner to dig, a Builder steps aside onto a floor beside it if there is one (`stepAside`); it builds nothing to wait on
- Sprinter: `parkour`
- Spiders: `climbWalls` (`WALL_CLIMB` / `WALL_DOWN` steps through mid-air cells beside a wall; the controller presses the spider against the wall, and vanilla spider physics does the climbing)

**Pathing** (`pathing/`) — `MobPathfinder` is a Baritone-style weighted A* over feet positions with `MoveType` moves. Each `PathStep` lists the blocks to `breaks()` / `places()`. Costs are in `PathCosts`; work is deliberately priced *below* its real time so specialists prefer digging and building to long detours.
- The search is limited per mob, per tick for all mobs together (`PathBudget`) and by time. It never loads chunks (`BlockView`), and it returns a partial path when it can't reach the goal.
- `SquadSupport` feeds squad context into the costs: work squadmates can do on request, work already claimed or planned on a worker's route (so squadmates follow a Builder's stair instead of asking for their own), crowded cells, and whether a Builder has followers (towers then cost extra, since nobody can follow one up).
- Goals report `rise()`, how far up they are; for mobs that build (or have a Builder to ask) the heuristic prices each level at `PathCosts.BUILT_CLIMB`, so searches for a high goal head up instead of flooding the ground. That climbing extra is left out of the heuristic's weighting, and bridges over air cost `AIR_BRIDGE` extra, so Builders walk to the foot of a climb first and climb last.
- What Builders build is meant to be followed. Up: a ladder on a wall, or up the side of a pillar of support planks (`supportCost`), or a stair for a level or two (`STAIR` is priced above a ladder level). Stairs never turn: `keepsHeading` checks the run of built floor back to the last stair step, and a re-plan that starts on a built stair keeps to its line. Towers (`PILLAR`) are a last resort. Down, where a drop is more than `pathing.maxFall`: a ladder over the edge climbed down rung by rung, or a straight stair down (`buildDown`, `climbDown`).
- Ladders are not floors: a mob only walks sideways into a ladder cell that has a floor under it (otherwise it gets on from below, or steps down onto the top), and diagonal moves never start on a ladder. Yield routes (`findYieldRoute`) follow the same rule. A route doesn't end hanging on a ladder if the goal has a floor anywhere (`hangsOnLadder`), so a mob that has arrived isn't left blocking the top of the squad's ladder.
- Search nodes are keyed by position only, so a move's work is remembered through the parent chain: `isPlannedPlank` / `isPlannedLadder` look a few steps back, and moves into a plank planned there are rejected.
- `AccessCorridors` keeps the air of built routes from being built into. A step is reserved when a mob arrives on it, never when it is only planned.
- Sprinters and spiders (`PathAbilities.prefersBaritone()`) plan with Baritone when `BaritonePrediction.isAvailable()` (client or LAN only), and otherwise with the mod's own planner. Only `baritone/BaritonePlanner` may touch Baritone classes, so the mod still loads without Baritone. Spider climbing in the fork is `MovementWallClimb` (`allowWallClimb` on the request).

**Following paths** — `entity/ai/MobPathController` runs each step in the same order: clear the step (dig or blast, or ask for help), build what it stands on, then move. Several overlapping recovery systems live here and interact:
- step timeout
- `watchProgress` (the mob has stopped making progress)
- `watchTask` (a worker blocked from its task: first back-off, then re-plan)
- `tryAnotherWay`, which avoids the cells where it got stuck for a while
- yielding (`makeWay` / `clearTheWay` / `backOff`, ranked so working mobs take priority)
- holding for a Bomber's breach
- a Builder's plank put into a step's body cells since it was planned (`builtInto`), or work that can't be done: on its own route the mob re-plans; on a job, `cantDo` calls the request off (`Squad.cancel`) so the asker re-plans rather than the job repeating

**Squads** (`squad/`) — all coordination is local to a squad. `Squads` re-forms them every 20 ticks:
- stragglers leave
- merges up to `squads.maxSize` that avoid two of the same worker type
- spare workers are shared with nearby squads that lack them
- idle Bombers are lent to squads stuck at a wall
- each squad is given a side of the Core to attack from (`SIDES`)

`Squad` holds sightings, `WorkRequest`s (a stretch of the asker's own path, which the helper walks to and follows), block claims, the work left on workers' routes, crowd cells and breach warnings. When a worker's route with work on it changes (`routeChanged`: a new one, or one finished or dropped), squadmates whose routes need help nobody has planned re-plan to follow it or use what it built; a mob that reaches planned work waits for it (a step back if the spot is taken) rather than calling for help. Squads are not saved.

**Workbench** (`workbench/`) — a Rust-style tech tree. Wave mobs (tagged `WaveManager.WAVE_TAG`) drop Scrap (`waves.scrapChance`); Scrap researches items at a Workbench, and researched items are crafted from their craft cost.
- The tree is datapack data, one file per item in `data/<ns>/untildawn/tech_tree/` (`TechNode` documents the format; `TechTree` loads it). Results are `ItemStackTemplate`s, because `ItemStack`s can't be built while datapacks load.
- The server owns everything (`Workbench` checks every request); the client screen (`client/WorkbenchScreen`) only shows the `WorkbenchPayloads.State` it is sent and asks with `Action`.
- Research lives on the bench (`WorkbenchBlockEntity`) and moves with it as the `untildawn:research` component (copied by its loot table).
- `WorkbenchClientGameTests` opens the screen and saves screenshots to `build/run/clientGameTest/screenshots`.
- A tree is just a node with no `parent`; the screen lays each one out beside the last. The **Fortification** tree (Primitive Barricade → Reinforced / Spiky Barricade → Crossbow Sentry, Spike Trap) is the second.

**Weapons** (`item/`) — crossbows unlocked in the Workbench, all rooted at the Arrows node (a top: 16 arrows for sticks, flint and Scrap). `WindingCrossbowItem` redoes the normal crossbow's private winding (`windTicks`, `load`), and takes arrows only. `RepeaterCrossbowItem` winds 3 arrows and fires one volley per pull at 0.8x speed (Multishot makes each loaded arrow three and each pull a volley). `HeavyCrossbowItem` winds 1.5x as slowly and shoots 2x as fast; Power fits it and Multishot doesn't, done by overriding `power.json` and `multishot.json` in `data/minecraft/enchantment/` to read `untildawn:enchantable/*` tags (and Power is added to the `exclusive_set/crossbow` tag). `ScopedHeavyCrossbowItem` zooms when used while loaded (`ScopedCrossbowMixin` makes `Player.isScoping` true for it) and fires on the left click, which the client sends as `Shoot` because the game ignores the attack key while an item is in use. `recipe/ScopeRecipes` mounts a Scope (Heavy Crossbow + Scope) and dismounts it (Scoped alone gives the Scope and leaves the crossbow), carrying the components over. `ChainCrossbowItem` (under the Repeater Crossbow) never holds an arrow: while right click is held `onUseTick` takes one from the inventory and fires whenever `shotsBy(ticksHeld)` goes up (1 shot a second rising to 4 over 40 ticks); it is left out of the `enchantable/crossbow` tag, so Quick Charge, Piercing and Multishot do not fit it. Their models are the vanilla crossbow's, as placeholders.

**Pikes and the Grappling Gun** (`item/`, `ModItems.pike`) - the Pike tree (Wooden, Stone, Copper, Iron, Diamond Pike) is its own tree; each costs the matching sword, one more plank/stone/ingot/diamond and a stick. Pikes are swords with +0.5 `ENTITY_INTERACTION_RANGE` and an attack speed 10% lower (`PIKE_ATTACK_SPEED`); they are in the `minecraft:swords` tag so sword enchantments fit. The Netherite Pike is not in the tree: a smithing recipe upgrades the Diamond Pike. `GrapplingGunItem` (a tech tree root) hooks the block or mob in view within 20 blocks and pulls the user toward it every tick while use is held (the hook sticks to a mob); crouching on a mob pushes the mob toward the user instead, and crouching on a block does nothing.

**Core screen** (`game/CoreInterface`, `client/CoreScreen`) � using the Core opens a Workbench-styled screen: the next night's mob count, the Core's health, and three buttons. Start Night sets `CoreState.skippingToNight` (not saved), and `WaveManager.tick` then adds `SKIP_TICKS_PER_TICK` to the clock each tick until night. Repair costs 1 Scrap per health, 10 health a press. The Encyclopedia button opens a second page of the screen: the wave mobs (`CorePayloads.MOBS`) and a summary of the one picked (`encyclopedia.untildawn.<id>` in the lang file). The server says which are known (`CorePayloads.State.known`: those whose `<mob>.firstNight` has ended) and the rest show as ???, so add a mob there and in the lang file when adding one. The server checks every request, as for the Workbench.

**Fortifications** (`block/`) — player-built defences. `BarricadeBlock` is a thin wall three blocks tall (`PART` bottom/middle/top, `FACING` away from the placer), and breaking any part removes all three. Its own strength is the hardness from the front; `backHardness` applies when the miner stands behind it (`getDestroyProgress`). `SpikyBarricadeBlock` hurts touching and breaking entities, and both it and `CrossbowSentryBlock` work on scheduled ticks. The Sentry is a 1x1 face-attached block (dispenser model) that shoots the nearest `Enemy` it can see within a 160 degree cone (out from a wall, up from a floor, down from a ceiling; floor and ceiling use the vertical dispenser model). `SpikeTrapBlock` is a floor/wall/ceiling plate with no collision and a `READY` cooldown. The wall models (`wall_bottom/middle/top`) are placeholders; the Spike Trap uses `spike_trap` while armed and `spike_trap_up` (spikes out) while it re-arms after triggering.

**Config** — `config/UntilDawnConfig` registers every `Setting` (key, description, default, range). It is saved as commented JSON5 in `config/untildawn.json5` and editable in game with `/monster config`. A new setting needs only an `add(Setting.of…)` line; the file is rewritten with it on the next load.

**Commands** — `/untildawn` (operators only) is in `command/UntilDawnCommand`: `path show|hide|info|recompute|predict`, `night`, and `config`. The class Javadoc lists the syntax.

**Mixins** — `untildawn.mixins.json` lists `MobArrowMixin` (arrows from Archers and Longbow Men damage the first block they hit and leave a trail, both tracked with the entity tags in `ArcherSkeleton`) and `CreeperAccessor`.

## Conventions

- Code is documented with short Javadoc saying *why*, in plain game terms. Match that density, and keep class Javadocs accurate when behaviour changes.
- Source files mix LF and CRLF line endings. Keep each file's existing endings.
