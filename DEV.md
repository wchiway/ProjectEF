# ProjectEF Neo — Developer Guide

[← Back to README](README.md)

This guide covers building and running ProjectEF Neo from source. For gameplay features, installation, and optional integrations, see the [README](README.md).

## Project References

* [Documentation index](docs/README.md) — architecture, examples, and historical material.
* [Architecture](docs/architecture.md) — current module responsibilities and compatibility boundaries.
* [Fabric port status](FABRIC_PORT_PLAN.md) — the source of truth for porting decisions, progress, and remaining work.
* [Gradle properties](gradle.properties) — configured project and dependency versions.
* [Build configuration](build.gradle) — Fabric Loom setup, dependencies, and source sets.

## Source Sets and Validation

| Location | Current status |
|---|---|
| `src/api/java` | Compiled public API; included in the main JAR and also published as an API JAR |
| `src/main/java` and `src/main/resources` | Compiled Mod code and runtime resources |
| `src/datagen/generated` | Tracked resources included in the main JAR; do not delete them |
| `src/datagen/java` | Retained for migration, but not compiled; no `runData` run is currently configured |
| `src/test/java` | Retained for migration, but the test source directories are currently disabled |

A successful build or test task does not mean the retained tests ran. Restoring tests and data generation is separate work tracked in the Fabric port status. Client, dedicated-server, and multiplayer checks must be recorded separately from compilation or documentation checks.

## Local Workspace

The Gradle wrapper defaults `GRADLE_USER_HOME` to the repository's `.gradle-home/`. Keep it in place; deleting it is not necessary for directory cleanup. IDE Gradle integrations may need this path configured separately.

The `.gradle/`, `.gradle-home/`, `build/`, and `run/` directories are local build or runtime state, not source material. Exclude them from IDE indexing where appropriate. Back up test worlds before removing anything under `run/`.

Shared documentation, examples, and scripts belong in `docs/`, `examples/`, and `scripts/`. Use `docs/local/` or `scripts/local/` for private notes and experiments. Existing local plans, legacy check scripts, logs, and extracted dependencies remain ignored; review them individually before adding them to Git.

## Building from Source

Use Java 21 and the Gradle wrapper included in this repository.

Clone the repository:

```bash
git clone https://github.com/wchiway/ProjectEF.git
cd ProjectEF
```

Build the project on Linux / macOS:

```bash
./gradlew build
```

Windows (PowerShell):

```powershell
.\gradlew.bat build
```

Build output is written to `build/libs/`.

## Running the Development Client

Linux / macOS:

```bash
./gradlew runClient
```

Windows (PowerShell):

```powershell
.\gradlew.bat runClient
```

### Selecting a Recipe Viewer

The development recipe viewer defaults to JEI. The `recipe_viewer` property in `gradle.properties` accepts `jei`, `emi`, `rei`, `hybrid`, or `none`.

To use EMI or REI for a single run:

```bash
./gradlew runClient -Precipe_viewer=emi
./gradlew runClient -Precipe_viewer=rei
```

On Windows (PowerShell):

```powershell
.\gradlew.bat runClient -Precipe_viewer=emi
.\gradlew.bat runClient -Precipe_viewer=rei
```

## Porting Notes

Before working on loader-specific behavior, read the [Fabric port status](FABRIC_PORT_PLAN.md). It tracks current decisions and separates confirmed implementation facts from historical items that still need verification. Earlier migration records are linked from the [documentation index](docs/README.md).

The mod identifier remains `projecte`, but NeoForge add-ons are not binary-compatible with the Fabric API changes and need to be ported separately.
