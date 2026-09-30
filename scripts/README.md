# Development scripts

[Developer guide](../DEV.md) · [Documentation index](../docs/README.md)

This directory is for reviewed, reusable development and release scripts. Shared scripts are tracked; private experiments belong in the ignored `scripts/local/` directory.

The existing local `verify-fabric-runtime-regressions.sh` remains ignored pending review. It checks source-text patterns, is not part of the supported build or CI, and must not be treated as proof of runtime behavior. It was not changed or promoted into the repository during the documentation cleanup.

For each shared script added later, document its prerequisites, invocation, and whether it changes files. Keep release automation changes and test restoration separate from directory-only cleanup. Until then, use the existing build commands in the developer guide.
