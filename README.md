<p align="center">
  <img src="src/main/resources/assets/projecte/logo.png" alt="ProjectEF Neo Logo" width="220">
</p>
<h1 align="center">ProjectEF Neo</h1>
<p align="center">
  <b>Equivalent Exchange: Reborn for Fabric</b><br>
  中文名：[Fabric]等价交换 Neo<br>
  Minecraft 1.21.1 · Fabric
</p>
<p align="center">
<!-- <a href="https://modrinth.com/mod/projectef">
<img src="https://img.shields.io/modrinth/dt/projectef?logo=modrinth&label=Modrinth%20Downloads&color=00AF5C" alt="Modrinth Downloads">
</a> -->
<a href="https://www.curseforge.com/minecraft/mc-mods/projectef">
<img src="https://img.shields.io/curseforge/dt/1618732?logo=curseforge&label=CurseForge%20Downloads&color=F16436" alt="CurseForge Downloads">
</a>
<a href="https://github.com/wchiway/ProjectEF/releases">
<img src="https://img.shields.io/github/v/release/wchiway/ProjectEF?logo=github&label=Latest%20Release" alt="Latest Release">
</a>
<a href="https://github.com/wchiway/ProjectEF/blob/mc1.21.1/LICENSE">
<img src="https://img.shields.io/github/license/wchiway/ProjectEF?label=License" alt="License">
</a>

</p>

## Quick Links

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/projectef) · [GitHub Releases](https://github.com/wchiway/ProjectEF/releases) · [Installation](#installation) · [Report an Issue](https://github.com/wchiway/ProjectEF/issues) · [Developer Guide](DEV.md)

## Overview

**ProjectEF Neo** is a modern Fabric port of the classic **ProjectE** mod — the complete **Equivalent Exchange** experience on Minecraft 1.21.1.

The **"F"** stands for **Fabric**. ProjectEF Neo keeps the classic **EMC (Energy-Matter Covalence)** gameplay intact:

* Convert items into EMC and permanently learn their value.
* Recreate learned items anytime through the Transmutation Table or the portable Transmutation Tablet.
* Automate EMC production with Collectors, Relays, and Condensers.
* Progress from the Philosopher's Stone to Dark Matter / Red Matter tools and GEM armor.

Existing ProjectE configuration files, resource paths, datapacks, and world data remain compatible wherever possible.

## Features

### EMC & Transmutation

* Item ↔ EMC conversion with permanent item knowledge.
* Transmutation Table and portable Transmutation Tablet.
* Configurable custom EMC values (`custom_emc.json`, in-game EMC Manager, or commands).
* Press **F8** (rebindable in Controls) or use **EMC Manager** in the pause menu. Search by item name/ID or select your held item's base type, then save, remove, or reset its EMC. Click **Apply** to recalculate and synchronize all saved changes without `/reload`.
* Editing requires the corresponding ProjectE `set_emc`, `remove_emc`, or `reset_emc` command permission (OP level 2 by default); applying requires all three. Remove saves a zero override; Reset removes the base-item override. Tags and component-specific variants are not edited by this screen.

### EMC Generation & Automation

* Energy Collectors (MK1–MK3)
* Anti-Matter Relays (MK1–MK3)
* Energy Condensers (MK1/MK2)
* Dark Matter Pedestal

### Equipment & Utility Items

* Philosopher's Stone — block transmutation and world conversion.
* Dark Matter and Red Matter tools with configurable area mining.
* Dark Matter Furnace and Red Matter Furnace.
* Alchemical Bags and Eternal Density gems.
* Swiftwolf's Rending Gale.
* Full GEM Armor abilities: night vision, step assist, flight, and combat effects.

### Trinkets Integration

Accessory slots are provided through [Trinkets](https://modrinth.com/mod/trinkets): equip rings, amulets, charms, and Klein Stars in the `hand/ring` and `chest/necklace` slots. ProjectEF Neo works without Trinkets — accessories simply behave as regular inventory items.

### Compatibility & Localization

* Built-in EMC values for classic Avaritia items are **disabled by default**. To opt in, set `enableAvaritiaEMC = true` at the top level of `config/ProjectE/mapping.toml`, then restart the server or reload the world. If `usePregenerated` is enabled, disable it first so EMC values are recalculated. This switch does not block custom EMC values or values derived from recipes.
* AvaritiaNeo Fabric: automatically enables Dark Matter and Red Matter Singularities, with compressor recipes consuming 200 corresponding ProjectE matter blocks. Item Alchemy is not required.
* Full Simplified Chinese and English localization.
* Compatible with original ProjectE configuration format and mod ID (`projecte`).

## Installation

### Requirements

* Minecraft 1.21.1
* Fabric Loader 0.16.9 or newer
* Fabric API (1.21.1 compatible version)
* Java 21

> Forge Config API Port and fabric-permissions-api are bundled inside the ProjectEF Neo JAR (jar-in-jar). **No extra downloads needed.**

### Steps

1. Install Minecraft 1.21.1 with Fabric Loader.
2. Install a compatible Fabric API version.
3. Download ProjectEF Neo from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/projectef) or [GitHub Releases](https://github.com/wchiway/ProjectEF/releases).
4. Place the JAR file into your Minecraft `mods` folder.

### Optional Integrations

These mods only enhance the experience and are never required:

* JEI / EMI / REI — recipe viewers
* Jade / WTHIT — block information overlays. With Jade installed, ProjectEF Neo registers its own EMC provider through Jade's Fabric plugin entrypoint; no Jade patch is required. Blocks with an EMC value display it when looked at. Keep `misc.lookingAtDisplay` enabled in ProjectE's `server.toml` and **EMC Provider** (`projecte:emc_provider`) enabled in Jade's plugin settings.
* Trinkets — accessory slots

## Compatibility Notes

ProjectEF Neo keeps the original ProjectE mod identifier:

```
projecte
```

This allows compatibility with:

* Existing configuration files
* Existing resource paths
* Existing world data

The following integrations from the NeoForge version are not included:

* CraftTweaker
* The One Probe
* Curios (replaced by Trinkets on Fabric)

## Reporting Issues

Please [open an issue](https://github.com/wchiway/ProjectEF/issues) and include:

* ProjectEF Neo version
* Minecraft version
* Fabric Loader version
* Fabric API version
* Steps to reproduce
* Relevant game logs

Please attach logs as files or external paste links instead of posting complete logs directly in the issue.

## Development

For source builds, development runs, and source-set status, see the [Developer Guide](DEV.md). Architecture notes, examples, and historical material are listed in the [documentation index](docs/README.md).

## Maintainer

**Chiway Wang**

Fabric port developer and maintainer.

## Credits

ProjectEF Neo is based on the original **ProjectE** project.

Special thanks to:

* ProjectE developers and contributors
* EE2 original creators
* Minecraft modding community

Original contributors include:

* SinKillerJ
* pupnewfster
* MaPePeR
* williewillus
* Lilylicious
* MozeIntel
* Kolatra

Additional credits:

* x3n0ph0b3 — EE2 creator and original asset permissions
* MidnightLightning — EE2 GUI textures

## License

ProjectEF Neo is released under the MIT License.
