# Changelog

All notable changes to ProjectEF Neo are documented here.

## [1.3.2] - 2026-10-02

### Added

- In-game EMC Manager, opened with F8 (rebindable) or from the pause menu. Search base items by name or registry ID, select the held item's base type, and save, remove, or reset custom EMC values without commands.
- Server-side permission checks, conflicting-edit detection, safe persistence, and an Apply button to recalculate and synchronize saved EMC changes across the server, including when pregenerated values are enabled.
- Simplified Chinese and English manager translations, plus rendering-order and translation consistency checks.

### Fixed

- EMC Manager titles, values, and status text being covered by a second background render pass. Small windows now show a scaling hint instead of overlapping controls.
- Jade EMC display by registering the Fabric plugin entrypoint.

### Changed

- Built-in EMC values for classic Avaritia items are disabled by default. Custom overrides and recipe-derived values remain unaffected.
- Updated release and update-check metadata to 1.3.2.

## [1.2.3] - 2026-08-10

### Fixed

- Nova Catalyst and Nova Cataclysm explode with their proper power again (16 and 48) instead of a vanilla TNT blast (4.0). `PrimedTnt#explode` is `protected` in NeoForge but `private` in vanilla, so the port replaced the `explode()` override with a `tick()` override that never actually took over the detonation — `super.tick()` had already run the vanilla explosion, leaving `getExplosionPower()` and the whole `NovaExplosion` path as dead code. The method is now widened via the access widener and overridden as before.
- Nova Catalyst and Nova Cataclysm no longer turn into vanilla TNT when ignited. Fabric has no `TntBlock.onCaughtFire` hook (the NeoForge version routes every ignition path through it), so any path not explicitly overridden fell through to the static `TntBlock.explode` and spawned a plain `PrimedTnt`. `ProjectETNT` now overrides all six ignition-related `TntBlock` methods, and two mixins cover the paths that call the static method directly:
  - `TntBlockMixin` intercepts `TntBlock.explode` and covers every caller that still has the block in place — most notably the vanilla flint and steel dispenser behavior, which ignites *before* removing the block.
  - `FireBlockMixin` redirects the call in `FireBlock.checkBurnOut`. That path removes the block *before* igniting, so the `BlockState` has to be cached beforehand.
- Fire spread can now ignite the Nova blocks at all. They were never registered as flammable, so `FireBlock.checkBurnOut` never reached them.

## [1.2.2] - 2026-08-04

### Fixed

- The Philosopher's Stone is no longer consumed when used as a crafting ingredient, and its EMC is no longer added to the result. `Item#getCraftingRemainingItem` is `final` in 1.21.1, so the stone is now registered as its own crafting remainder via an access widener. This also zeroes out its net contribution in `BaseRecipeTypeMapper`, which subtracts the remainder's EMC from the ingredient cost.
- Downgraded Cardinal Components API to 6.1.2. Version 6.1.3 is listed in the Ladysnake Maven metadata but its artifacts are not actually resolvable, which broke CI builds.

### Changed

- Renamed to **ProjectEF Neo** (Chinese: 等价交换Neo).

## [1.2.1] - 2026-07-31

### Fixed

- Worn accessories no longer lose their passive abilities. Trinkets keeps equipped stacks in a Cardinal Components inventory rather than the player's, so vanilla never called `Item#inventoryTick` for them; `ProjectETrinket` now forwards the trinket tick to it. This also closes a free-flight exploit where a worn Swiftwolf's Rending Gale granted flight without ever draining EMC.
- Accessory slot tags now actually restrict what fits where. The shipped `trinkets:all` tag made every ProjectE accessory equippable in *every* slot — including slots added by other mods — because Trinkets' `trinkets:tag` validator accepts an item in the slot tag **or** in `trinkets:all`.
- Time Watch is registered as a Trinket. It was listed in the `chest/necklace` tag but never passed to `TrinketsApi.registerTrinket`.
- Void Ring is listed in the `hand/ring` tag. It previously relied on `trinkets:all` and would otherwise have become unequippable.
- Stacks mutated in place inside an accessory slot now sync to the client. Draining a worn Klein Star for EMC or repairing a worn item bypassed `insertItem`/`extractItem` and never signalled the Trinkets inventory, so the client kept showing stale values.
- `.gitignore` no longer ignores the Trinkets integration sources and datapack. A `trinkets/` pattern without a leading slash matched those directories at any depth, so newly added files there would have been silently untracked.

### Changed

- `IItemHandler` gains `markSlotChanged(int)`, a default no-op for consumers that mutate a stack returned by `getStackInSlot` in place. `ItemStackHandler` bridges it onto its existing `protected onContentsChanged(int)` hook, which also covers the Repair Talisman's alchemical bag and chest paths.
- Release notes now list every published artifact with its size and purpose, so it is clear which jar players should install and which are for addon development.

### Removed

- `IExposesCurioAttributes` and the `ProjectETrinket.getModifiers` override. The sole implementor contributed no modifiers, making the whole path equivalent to Trinkets' built-in default trinket.

## [1.2.0] - 2026-07-29

### Added

- REI (Roughly Enough Items) recipe viewer integration via the shared `RecipeViewerHelper` abstraction, with Energy Collector fuel upgrade and World Transmutation display categories and workstation registrations.
- Trinkets accessory integration replacing the removed NeoForge Curios support.
  - 19 ProjectE items (rings, amulets, charms, Klein Stars) are registered as Trinkets and can be equipped in the `hand/ring` and `chest/necklace` slots.
  - Trinkets inventory is exposed as `IItemHandler` for ProjectE's polling-based systems (fuel consumption, Repair Talisman, hotbar-or-accessory ability checks).
  - Arcana ring flight works while worn: `InternalAbilities.shouldPlayerFly` polls the Trinkets inventory through `PlayerHelper.checkHotbarCurios`.
  - Data-driven slot assignment: `data/trinkets/entities/projecte.json` grants `hand/ring` and `chest/necklace` slots to players.
  - Item tags: `trinkets:hand/ring`, `trinkets:chest/necklace`, and `trinkets:all` ensure items are accepted by Trinkets slot validators.
- `rei_client` entrypoint in `fabric.mod.json` for the REI plugin.
- `trinkets` `modLocalRuntime` dependency for dev environment testing.

### Changed

- `PETags` curios tag constants updated to Trinkets `group/slot` format (`hand/ring`, `chest/necklace`, `legs/belt`).
- `PEItemTagsProvider` datagen tag assignments aligned with new Trinkets tag paths.
- `buildSrc` directory restored (was previously deleted).
- `.gitignore` updated to properly track Trinkets integration files.

### Removed

- Stale datagen-generated Curios tag files (`data/curios/tags/item/`).
- NeoForge Curios placeholder references in platform documentation.

### Known Limitations

- Trinkets accessory rendering on the player model (`TrinketRenderer`) is not implemented.
- REI item subtype comparison (distinguishing items by mode or stored EMC) is not yet ported.
- Ring passive tick effects remain gated by the `hotBarOrOffHand` inventory slot check, matching the existing item design.

## [1.1.0] - 2026-07-21

### Fabric Port

This release introduces the Fabric 1.21.1 edition of ProjectE under the name **ProjectEF**. The `F` stands for Fabric.

### Added

- Fabric Loader support for Minecraft 1.21.1.
- Fabric-safe public API primitives, registries, events, networking, capabilities, and data attachments.
- EMC mapping, recipe mapping, custom conversions, world transmutation, and server-to-client synchronization.
- ProjectE items, blocks, entities, recipes, containers, block entities, and client rendering.
- Energy Collectors, Anti-Matter Relays, Energy Condensers, Dark Matter Furnaces, Red Matter Furnaces, and related automation support.
- Transmutation Table, Transmutation Tablet, Philosopher's Stone, Alchemical Bags, Pedestals, and Eternal Density functionality.
- JEI and EMI recipe viewer integrations.
- Optional Jade and WTHIT integrations when those mods are installed.
- Built-in EMC values for classic Avaritia items.
- Fabric-compatible recipe load conditions for generated recipes and advancements.
- Expanded Simplified Chinese translations and updated localization metadata.

### Restored and Fixed

- Creative flight and ring-provided flight behavior.
- GEM armor abilities, including helmet night vision and boot step assistance.
- Dark Matter and Red Matter tool area modes, including 3x3-style modes.
- Philosopher's Stone world transmutation, conversion cycling, selection range rendering, and recipe interactions.
- Dark Matter and Red Matter Furnace fuel handling for ProjectE fuels.
- Volcanite Amulet lava placement and projectile behavior.
- Pedestal item rendering for rings and other supported items.
- `/project` client diagnostics command registration and execution.
- EMC reload behavior after editing EMC configuration or mapper data.
- Red Matter Sword and Red Matter Katar instant-kill modes for high-health entities.
- Fabric permissions compatibility for Minecraft 1.21.1 and Xaero map integrations.

### Platform and Compatibility Changes

- Replaced NeoForge build tooling with Fabric Loom 1.10.5, Mojang mappings, and Parchment mappings.
- Added Fabric access wideners, mixin configuration, and Fabric client/server entrypoints.
- Added Forge Config API Port as a required runtime dependency.
- Bundled the Minecraft 1.21.1-compatible `fabric-permissions-api` 0.3.1 release.
- Preserved the internal mod ID `projecte` and the existing `config/ProjectE` configuration directory.
- Removed or deferred NeoForge-only integrations that do not have a compatible Fabric 1.21.1 implementation, including CraftTweaker and The One Probe.
- Artifact names use the format `ProjectEF-1.21.1-PE1.1.0.jar`.

### Requirements

- Minecraft 1.21.1.
- Fabric Loader 0.16.9 or newer.
- Fabric API for Minecraft 1.21.1.
- Forge Config API Port 21.1.0 or newer.
- Java 21.

### Known Limitations

- This release targets Fabric only; NeoForge-specific integrations are not available.
- REI and Trinkets integrations are now available as of version 1.2.0.
- The legacy NeoForge test framework and data generator are not part of the current Fabric build.

