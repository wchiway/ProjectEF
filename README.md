# ProjectEF

ProjectEF is a focused Fabric port of the parts of ProjectE that fit this project: a practical EMC shop built around the Transmutation Table and Transmutation Tablet.

It is designed for **Minecraft 26.3** with **Fabric**. The goal is to make EMC useful for farmed resources and normal survival progression without bringing back the parts of ProjectE that add unnecessary grinding or complexity.

## Current version

**1.1.33-fabric.26.3-preservation.2**

[Download the current Minecraft 26.3 JAR](downloads/projecte-fabric-26.3-1.2.9-fabric.26.3.jar?raw=true)

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
- Energy Condenser
- Dark Matter Furnace
- Red Matter Furnace
- Custom ProjectE-style items, including the Black Hole Band

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
