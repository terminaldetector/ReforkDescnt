# Native gravity-tool input: first launch instrumentation failure

Tested source: `c76c984984b4a601132e9566fcabf64fd3b0f916`, based on
`f3d97844b2335d8fb9700960a12377fd8d3c2ca9`. This is a test-only observer;
production input, loadout and gravity-tool behavior were not modified.

## First result and scope

The first offline `remapJar remapCubiccheckJar --no-daemon` build passed. The
official dedicated native-world staging passed. The single fresh GL 3.2 native
process then exited **255**, without timing out, during mixin application before
world initialization. The Java lease was returned immediately on process close.
No correction or retry was run under that lease.

`GravityInputItemUseProbe` required injections at named `use`, with
`remap=false`. The remapped production class contains instead:

```text
DescentWeaponItem.method_7836(
  net.minecraft.class_1937, net.minecraft.class_1657, net.minecraft.class_1268
) -> net.minecraft.class_1271
```

The exact original error is `InvalidInjectionException: Critical injection
failure: @Inject annotation on before could not find any targets matching 'use'`.
The Minecraft 1.21.1 Yarn mapping identifies this as the overridden
`Item.use(World, PlayerEntity, Hand): TypedActionResult` method.

This is an **observer mapping failure**, not a reproduced gameplay defect. No
world frames, observer failure/success marker or six-transaction result exists.
Ordinary and 6DoF real-button gravity-tool acceptance remains **OPEN**. Earlier
direct-item-use stock GameTest results retain their narrower scope.

## Exact inputs and evidence

- Main candidate JAR SHA-256:
  `3f1e116c198d0341f0c81a400198fa0b1755be62afcaa2e60370c7be6920a7fd`.
- Observer JAR SHA-256:
  `783f057a5b9f991776f65659e0ebf0f718f86c516202a38d241169e9415e9b47`.
- First bundle: `EVIDENCE/native-gravity-input-first-c76.zip`.
- Bundle SHA-256:
  `086d7ff0a9d4ac90b63cbe2d88678e995d7ce37b53da6df3769be57d0fe04a49`.

The bundle has 124 payloads with individual SHA-256 values and verified ZIP CRC.
It preserves the exact source archive/diff, both actual candidate JARs, the
executed read-only five-helper set and its manifest, official stager source,
compile/staging/native/controller logs and immediate exit receipts, actual
launch/input manifests, crash report and runtime configuration. It also preserves
all 88 files (21,244,219 bytes) in the fresh dedicated seed-world copy. The native
process failed before opening that world; this is a preserved input world, not a
successful playthrough or an updated gameplay save. Shared libraries and the
Minecraft-only derivative are identified by hashes rather than duplicated.

The unchanged budgets were observer wall **300 seconds**, external native
**360 seconds + 15 seconds termination grace**, total observer **3,600 server
ticks**, six transactions, at most four active owned fixture bodies. No gameplay
assertions or thresholds were weakened. The approved next change is restricted
to the observer's vanilla-override method mapping and requires its own source
revision, frozen execution helpers and separately granted run.
