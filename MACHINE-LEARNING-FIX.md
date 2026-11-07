# ProjectEF 1.2.7 — learning dropped machines

Selling a block with saved block-entity data previously credited its EMC but
skipped learning the item. Actually Additions attaches that data to broken
machines, including empty machines, which made them disappear from the table
without becoming available to purchase.

The learning step now removes block-entity data from its exemplar, retaining
ordinary item-level names and enchantments. The purchased block is empty and
uses its default machine state. Saved fluids, energy, inventories and machine
configuration are never copied into the learned exemplar. Normal container
and bundle contents checks still apply. The sold stack's EMC calculation is
unchanged. Items without block-entity data follow their existing learning path.

Install by replacing the previous ProjectEF JAR. Keep Automatic EMC 1.0.1 and
the existing AA / Omnia / Ex Compressum builds. Existing balances and knowledge
remain intact. Prior sales that skipped learning were never recorded as
knowledge; selling another copy after updating will learn the empty block.

## Validation

Run `./gradlew build` for standard tests. With the optional fixtures below in
`libs`, run `./gradlew -PtestWithAA build`:

- `actuallyadditions-beta.2.jar`: AA Fabric 0.3.0-beta.2
- `automatic-emc-1.0.1.jar`: Automatic EMC 1.0.1
- `exnihiloomnia-alpha.4.jar`: Omnia Fabric 0.5.0-alpha.4

The integration test places and harvests all four reported AA machines,
confirms each actual drop carries block-entity data, deposits it through the
table's input slot, verifies the EMC credit and learned item, then purchases it
and checks that no saved machine data is copied. A separate test retains the
filled-container restriction. Existing preservation tests also cover names,
enchantments, durability and saved-account serialization.

The preservation test harness was updated for the current four-field account
record and the current requirement to have the transmutation menu open.

Validated on Java 25: server and client compilation passed, and all 11 required
server game tests passed with the combined AA / Automatic EMC / Omnia fixtures.
