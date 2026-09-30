# Architecture

[Developer guide](../DEV.md) · [Documentation index](README.md) · [Fabric port status](../FABRIC_PORT_PLAN.md)

This page describes the current project layout, not a proposed package reorganization. Paths in the table are relative to the repository root; main-source package paths are relative to `src/main/java/moze_intel/projecte/`.

## Entry points and modules

| Area | Location | Responsibility |
|---|---|---|
| Common initialization | `PECore.java` | Wires configuration, ordered registration, capabilities, networking, resource reloads, lifecycle events, and integrations |
| Client initialization | `client/PECoreClient.java` | Wires screens, rendering, key bindings, client receivers, and client events |
| Public API | `src/api/java/moze_intel/projecte/api/` | Contracts for EMC, knowledge, inventories, capabilities, codecs, and components |
| API implementations | `impl/`, `emc/components/` | Provides implementations selected by the API service interfaces |
| EMC calculation | `emc/` and the API's `nss/` and `mapper/` packages | Normalizes stacks, collects conversions, calculates values, and maintains cached results |
| Registered game content | `gameObjs/` | Items, blocks, block entities, containers, screens, and registration definitions |
| Registration adapters | `gameObjs/registration/` | Isolates loader-specific registration behind the existing wrappers |
| Networking | `network/PacketHandler.java`, `network/packets/` | Registers game packets and synchronizes state between server and client |
| Configuration | `config/` | Loads configuration and provides cached values and custom EMC overrides |
| World transmutation | `world_transmutation/` | Loads and synchronizes block-conversion rules |
| Optional integrations | `integration/` | Connects supported mods; recipe viewers share the `recipe_viewer/` abstraction |

Client code is not yet contained entirely in `client/`. Screens, rendering, tooltips, and input helpers also live in `gameObjs/gui/`, `rendering/`, `events/`, and `utils/`. `network/` also contains commands and update checking; it is not exclusively a packet layer.

## API service loading

The interfaces `IEMCProxy`, `ITransmutationProxy`, `IPECodecHelper`, and `IComponentProcessorHelper` each load their implementation through `ServiceLoader`. Their provider declarations are under [META-INF/services](../src/main/resources/META-INF/services/). `ProjectEAPI` defines shared constants; it is not the service-loading entry point.

The first three services are implemented under `impl/`; the component processor helper lives under `emc/components/`. Keep interface names and provider declarations aligned when changing implementations.

## Resource reloads and EMC

Resource reload handling records that EMC needs recalculating. The data-pack synchronization callback then coordinates mapping and synchronization. Conversion inputs and normalized stacks feed the EMC calculation pipeline; packet handling distributes the resulting state to clients.

World transmutation has its own reloadable rule set. Do not conflate these data resources with documentation examples or move them out of their configured resource roots.

## Compatibility boundaries

- Keep the mod ID `projecte`, configuration directory `ProjectE`, public API package names, registry identifiers, and resource paths stable. These names are compatibility contracts, not leftover branding to remove.
- Preserve the registration wrappers and their dependency ordering rather than rewriting every registration call site.
- Keep client-only dependencies out of common initialization paths; package organization alone does not prove dedicated-server safety.
- Extend the shared recipe-viewer abstraction when adding viewer support.
- Internal Curios method names currently delegate to Trinkets. Renaming them is a separate source refactor, not part of documentation cleanup.
- `src/datagen/generated` is a live resource input even though datagen Java sources are not compiled. See the [source-set status](../DEV.md#source-sets-and-validation).

Current implementation and validation gaps belong in the [Fabric port status](../FABRIC_PORT_PLAN.md), not in a second checklist here.
