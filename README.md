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

## Overview

**ProjectEF Neo** is a modern Fabric port of the classic **ProjectE** mod — the complete **Equivalent Exchange** experience on Minecraft 1.21.1.

The **"F"** stands for **Fabric**. ProjectEF Neo keeps the classic **EMC (Energy-Matter Covalence)** gameplay intact:

* Convert items into EMC and permanently learn their value.
* Recreate learned items anytime through the Transmutation Table or the portable Transmutation Tablet.
* Automate EMC production with Collectors, Relays, and Condensers.
* Progress from the Philosopher's Stone to Dark Matter / Red Matter tools and GEM armor.

Existing ProjectE configuration files, resource paths, datapacks, and world data remain compatible wherever possible.

## Project Status

| Component    | Version               |
| ------------ | --------------------- |
| Minecraft    | 1.21.1                |
| ProjectEF Neo | 1.3.0               |
| Mod Loader   | Fabric Loader 0.16.9+ |
| Fabric API   | 0.116.14+1.21.1       |
| Java         | 21                    |
| Build System | Fabric Loom 1.10.5    |

## Features

### EMC & Transmutation

* Item ↔ EMC conversion with permanent item knowledge.
* Transmutation Table and portable Transmutation Tablet.
* Configurable custom EMC values (`custom_emc.json` / in-game commands).

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

* Built-in EMC values for classic Avaritia items.
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
3. Download the latest ProjectEF Neo release.
4. Place the JAR file into your Minecraft `mods` folder.

### Optional Integrations

These mods only enhance the experience and are never required:

* JEI / EMI / REI — recipe viewers
* Jade / WTHIT — block information overlays
* Trinkets — accessory slots

## Building from Source

Clone the repository:

```bash
git clone https://github.com/wchiway/ProjectEF.git

cd ProjectEF
```

Build the project:

```bash
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

Build output:

```
build/libs/
```

Run the Fabric development client:

```bash
./gradlew runClient
```

The development recipe viewer defaults to JEI. The `recipe_viewer` property accepts
`jei`, `emi`, `rei`, `hybrid`, or `none`.

To use EMI or REI:

```bash
./gradlew runClient -Precipe_viewer=emi
./gradlew runClient -Precipe_viewer=rei
```

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

When reporting a bug, please include:

* ProjectEF Neo version
* Minecraft version
* Fabric Loader version
* Fabric API version
* Steps to reproduce
* Relevant game logs

Please attach logs as files or external paste links instead of posting complete logs directly in the issue.

## Developer

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
