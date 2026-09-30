# Documentation

[Player guide](../README.md) · [Developer guide](../DEV.md)

## Current references

| Document | Purpose |
|---|---|
| [Developer guide](../DEV.md) | Building, development runs, source-set status, and local workspace conventions |
| [Architecture](architecture.md) | Current module responsibilities and compatibility boundaries |
| [Fabric port status](../FABRIC_PORT_PLAN.md) | The single source of truth for porting decisions and remaining work |
| [Examples](../examples/README.md) | Reference material for custom conversions |
| [Scripts](../scripts/README.md) | Shared tooling policy and the status of local checks |
| [Changelog](../CHANGELOG.md) | ProjectEF Neo change history |

## Historical material

Historical documents are preserved for context, not as instructions for the current Fabric build. Their versions, links, API details, and pending tasks may be outdated.

- [July 2026 Fabric port plan](migration/archive/2026-07-fabric-port-plan.md)
- Upstream ProjectE changelogs:
  - [Minecraft 1.7.10](archive/changelogs/Changelog.txt)
  - [Minecraft 1.8](archive/changelogs/ChangelogMC18.txt)
  - [Minecraft 1.9](archive/changelogs/ChangelogMC19.txt)
  - [Minecraft 1.10](archive/changelogs/ChangelogMC110.txt)
  - [Minecraft 1.12](archive/changelogs/ChangelogMC112.txt)

## Documentation images

The existing preview images are preserved without changes: [items](assets/previews/items1.png), [screen 1](assets/previews/screen1.png), and [screen 2](assets/previews/screen2.png). They are documentation assets, not resources used by the game.

The inherited [Patreon image](assets/archive/patreon.png) is archived for provenance; it is not an active sponsorship link. Game textures, models, icons, and other runtime assets remain under `src/`.

## Adding documentation

Keep README.md focused on players and DEV.md focused on getting started with development. Put longer technical explanations under `docs/` and link them from the relevant entry point. Update the root Fabric port status rather than maintaining a second active checklist.

Shared documentation is tracked. Use `docs/local/` for private notes. Existing local `docs/plans/` and `docs/superpowers/` notes remain ignored until individually reviewed; they are not part of the shared documentation.
