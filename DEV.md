# ProjectEF Neo — Developer Guide

[← Back to README](README.md)

This guide covers building and running ProjectEF Neo from source. For gameplay features, installation, and optional integrations, see the [README](README.md).

## Project References

* `FABRIC_PORT_PLAN.md` (when available in the local checkout) — the source of truth for porting decisions, progress, and remaining work.
* [Gradle properties](gradle.properties) — configured project and dependency versions.
* [Build configuration](build.gradle) — Fabric Loom setup, dependencies, and source sets.

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

Before working on loader-specific behavior, consult `FABRIC_PORT_PLAN.md` if it is available in your local checkout, or ask the maintainer for the current porting status. The plan tracks data generation, tests, integration support, and known differences from the NeoForge version.

The mod identifier remains `projecte`, but NeoForge add-ons are not binary-compatible with the Fabric API changes and need to be ported separately.
