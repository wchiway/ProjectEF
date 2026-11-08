# ProjectEF

ProjectEF is a focused Fabric port of the parts of ProjectE that fit this project: a practical EMC shop built around the Transmutation Table and Transmutation Tablet.

It is designed for **Minecraft 26.3** with **Fabric**. The goal is to make EMC useful for farmed resources and normal survival progression without bringing back the parts of ProjectE that add unnecessary grinding or complexity.

## Current version

**1.2.13-fabric.26.3**

[Download the current Minecraft 26.3 JAR](downloads/projecte-fabric-26.3-1.2.13-fabric.26.3.jar?raw=true)

Install Fabric Loader and Fabric API for Minecraft 26.3. Close Minecraft, remove
any previous ProjectEF JAR, and place the downloaded JAR in `mods/`. Install the
same ProjectEF version on clients and servers. Java 25 is required.

## Included

- Transmutation Table
- Transmutation Tablet
- Server-side EMC accounts and learned-item data
- Configurable preservation of named, enchanted, potion and other item variants
- Vanilla EMC values tuned for this project
- EMC support for registered mod items
- World-specific EMC overrides
- Energy Collectors and Antimatter Relays Mk I–III
- Energy Condenser and chargeable Klein Stars
- Dark Matter Furnace
- Red Matter Furnace
- Dark Matter Pedestal
- Custom ProjectE-style items, including the Black Hole Band

## Classic rings

Hold a ring and press **G** to toggle its aura. On the Ring of Arcana, **C**
cycles frost, ignition, wind, and harvest. **V** raises charge, **Shift+V**
lowers it, and **R** releases the charged ability. The ProjectE keybinds are
configurable, and each ring's tooltip lists its mouse controls.

### Dark Matter Pedestal

Craft it with five Dark Matter Blocks and four Red Matter. Right-click the
pedestal with a supported power item to mount it. Empty both hands and
right-click to toggle it, or sneak-right-click to take the band back. While the pedestal is ticking, a mounted Harvest Goddess Band grows and
harvests crops in a 9×9×9 area and drops the harvest on the ground.
Other supported items include the Black Hole Band, Watch of Flowing Time,
Evertide and Volcanite Amulets, Repair Talisman, and the Zero, Ignition and
Swiftwolf rings. The Body, Soul and Life Stones and Archangel's Smite also
have mounted effects.

## EMC machines and storage

A collector uses the light above it to generate EMC. Place it beside a relay
or a condenser to transfer EMC. The machine slots from left to right are
**input**, **Klein Star**, **target** (condenser only), and **output**. The final
slot is unused. A collector upgrades fuel placed in input, and a relay burns
valued input items for EMC. A relay also drains EMC from a star placed in input. A condenser copies the item in its target slot
using its own input or EMC from adjacent machines. A condenser with an empty
target keeps depositing input EMC into its owner's account.

Sneak-use a Klein Star to charge it from your account, or use it normally to
deposit the charge. You can also place a charged star in the table or tablet's
EMC input slot to drain it, then take the empty star back. A machine can charge
a star in its Klein Star slot. Gem
Armor and the Tome of Knowledge require fully charged Omega stars.

Use an Alchemy Bag to open 104 slots of storage shared by bags of that color
for your player. With both hands empty, press **V** to arm Gem Armor attacks,
**R** for the helmet's lightning, and **C** for the chestplate's shockwave.

## EMC shop

The table and tablet use the same shop system:

1. Sell a valued item to receive EMC.
2. Selling an item teaches it to that player.
3. Spend stored EMC to buy learned items.
4. Purchases recreate the learned item, including its saved properties when preservation is enabled.

The system is server-authoritative, so prices, balances, learning, selling, and buying are checked on the server.

Modded items are supported as long as they have a registered item ID and an EMC value. Items from namespaces other than `minecraft:` are not automatically blocked.

## Item-data preservation

ProjectEF creates `config/projecte-preservation.properties`:

```properties
preserve_item_data=true
preserve_custom_names=true
preserve_enchantments=true
preserve_potion_effects=true
preserve_custom_data=true
```

With preservation enabled, each distinct item configuration must be learned
separately. Learning an enchanted sword does not unlock its ordinary version.
Custom names, enchantments, potion effects, model identity and other data
components survive learning, purchasing and world restarts. Matching variants
are deduplicated. Durability wear is always ignored: different damage levels do
not create different learned entries, and purchases return undamaged items.
Existing wear-only duplicates are merged when the account is opened; ordinary
tools become ordinary learned items while enchanted/named/cosmetic versions
remain separate. This cleanup preserves balances and other saved components,
even when preservation switches are temporarily disabled. Cosmetic variants such as ItemBound equipment keep their
underlying item's EMC value; existing enchantment adjustments still apply.

The individual switches remove their named properties when learning or
retrieving variants. Set `preserve_item_data=false` to use base-item learning;
already saved variants stay stored and become available again when re-enabled.
Earlier configuration files are respected, so check the master switch after
upgrading from an early preservation build.

Existing EMC balances and ordinary learned-item records remain supported.
Filled containers and items carrying block-entity data can be sold but are not
learned with their contents. The survival blacklist applies to variant purchases
as well as ordinary items.

ProjectEF does not require ItemBound. A compatible variant addon can supply its
own names and models; ProjectEF preserves those components.

## Black Hole Band

The Black Hole Band uses this custom recipe:

- 4 Ender Pearls
- 4 Gold Ingots
- 1 Iron Block

Its EMC value is **14,536**, matching the total EMC of those ingredients.

## Survival-focused blacklist

Items that are not normally obtainable through survival gameplay are blocked from the EMC table, even if they are obtained through commands or another non-normal method.

This includes technical and creative-only vanilla entries such as:

- Bedrock
- End Portal Frames
- Command blocks
- Structure and test blocks
- Spawners and other technical spawner entries
- Infested blocks
- Budding Amethyst
- Reinforced Deepslate
- Creative-only spawn eggs
- Technical mob-face and legacy egg entries

Normal survival items remain available, including items such as the Dragon Egg, Turtle Egg, Sniffer Egg, Player Head, Wither Skeleton Skull, and Dragon Head.

## Commands

Operators can set an EMC value for the item being held:

```
/projecte setemc <emc>
```

Or set a value by item ID:

```
/projecte setemc <emc> <namespace:item_id>
```

Example:

```
/projecte setemc 24584 minecraft:diamond_pickaxe
```

Set an item to `0` to disable its EMC value. Changes are world-specific and persist in the world data.

## Building

Use the included Gradle wrapper with Java 25:

```
./gradlew build
```

On Windows PowerShell:

```
.\\gradlew.bat build
```

The project targets Fabric Loader `0.19.5`, Fabric API for Minecraft 26.3, and Fabric Loom `1.18.2`.

## Scope

ProjectEF is intentionally not a full restoration of every original ProjectE system. The table, tablet, EMC economy, selected utility items, condensers, and matter furnaces are the core of this version. Features that do not serve the simplified survival-focused design may remain absent.

For the detailed EMC registry and balancing notes, see [EMC-SHOP-README.md](EMC-SHOP-README.md).
