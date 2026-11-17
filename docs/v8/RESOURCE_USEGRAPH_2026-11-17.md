# Source registry/resource use graph — 17 November

**No missing referenced asset suitable for a production fix was found in this
scope.** No model/texture/ID was added, replaced or deleted. This is a source and
resource audit, not a fresh native item-atlas or all-model visual acceptance.

Exact game/resource pin: `9b7cf9b0709a47c181d113923f7ac59a4b62fd3c`. Later
canonical `8e34f3d9f34aa9a3727589be6c52b7b46ee6a849` has no changes in inspected
`src/main` or `scripts/audit_resources.py`; later evidence is not relabelled as a
new game run. The agent's older isolated game source was not used as the audit
input. All source/resource bytes were read from the immutable9b7 Git objects.

## Checked roots and endpoints

| Coverage | Result / limits |
| --- | --- |
| Source-defined item registrations |131 IDs,131 corresponding item model JSONs. Direct registry, helper `wep/register/egg` calls and BlockItem creation were reconciled. This is not runtime registry enumeration. |
| Source-defined block registrations |34 literal/helper definitions plus13 explicit OrganicMaterials-derived IDs =47. Carved and13 organic blocks intentionally have no item form; no missing item icon inferred. |
| Parent/override/texture graph | Actual inherited texture variables, blockstate variants/multipart and overrides resolve with0 missing/cyclic/unbound errors and0 missingno-placeholder edges. |
| Vanilla dependencies | Actual Minecraft1.21.1 official client resource JAR,26,836,906bytes, SHA `499f6897d1837516680f3114072d8106e11c9adcd933fe5cf051b551089b0c99`.3891 vanilla models were loaded, rather than assuming every minecraft: parent exists. |
| Mod model/PNG integrity |294 model JSONs,131 item models;119 mod PNGs decode/verify. Existing pinned geometry/display scanner also reports0 errors;9 same-geometry groups are review warnings, not removals. |
| Data consumers |251 extracted recipe/loot/item-reference edges resolve to source-defined items; this is not full datapack/balance validation. |
| Direct renderer texture literals |18 edges:17 packaged PNG references and1 legitimate runtime block atlas. Dynamic/non-literal/resource-pack behavior is not claimed universally analysed. |
| Source integrity |1122 pinned source/resource hashes,20 numbered source excerpts and11 synthetic graph controls. |

The [receipt](EVIDENCE/resource-usegraph-9b7cf9b.json) and
[compact proof](EVIDENCE/resource-usegraph-9b7cf9b.zip) preserve all131/47 root rows,
resource edges, exact source hashes, recipes, programmatic adapters, checker
source and real-JAR binding. ZIP CRC and every payload hash were re-read. No
Minecraft JAR, class bytecode or art image is duplicated in the proof package.

## Legitimate programmatic paths

- Minecraft `builtin/generated` and `builtin/entity` are loader markers, not
  missing JSON files. Cached named vanilla ModelLoader metadata independently
  contains both literals. `textures/atlas/blocks.png` is runtime-generated;
  the real packaged `atlases/blocks.json` definition is bound separately.
  Vanilla `textures/misc/white.png` genuinely exists in the official JAR.
- Carved's empty base JSON and organic adapters are intentional: the Fabric
  HybridTerrainModel hook emits geometry using the original source-state model.
  EntityModelLayerRegistry similarly binds programmatic entity geometry; absent
  standalone entity JSON is not an error.
- HybridWeaponModels defines three original Java meshes. WeaponViewRenderer
  routes supported IDs there and has explicit primitive fallbacks. It only
  replaces the ordinary hand when visible modules exist; other items retain
  their baked item renderer. An external OBJ/JSON is not required for these paths.
- ORB/ROCKET/JET/OblivionSeeker eggs share the vanilla spawn-egg template. Their
  source constructors supply colours; actual cached ItemColors metadata calls
  SpawnEggItem.getAll/getColor. Shared template does not mean missing or equal
  final icons. Their runtime tint/render acceptance remains separate.
- The current portal_projector and portal_stabilizer already have distinct3D
  JSON models. Legacy portal_gun references a valid raster named
  portal_stabilizer.png. Its filename alone does not prove duplication of the
  current stabilizer's3D icon. No arbitrary replacement model was invented.
- New sliding_bearing and linear_drive blockstates/block/item models all exist;
  their vanilla material/parent edges resolve.

## Use graph matters before cleanup

Five explicitly retired weapon IDs (`weapon_d6_heavy`, `weapon_d6_railmk2`,
`weapon_d6_reactor`, `weapon_d6_telefrag`, `weapon_d6_whiplash`) remain registered,
and each still has a real crafting-result recipe. Hidden from creative does
**not** mean dead/unreachable. Same-instance Java aliases are not extra registry
IDs. Removing recipes/IDs/assets requires a separate authorised compatibility
decision; none was made here.

Nineteen model resources are not reached from this registry/blockstate/override
root set, mostly older mirror composition variants. This is a bounded search
result, not proof against dynamic, external-pack or compatibility consumers and
not a deletion list. Shared geometry/templates also remain intentional review
candidates, not errors.

## Remaining real gaps and next bounded work

1. Current-source **runtime registry-labelled atlas and held-item matrix**:
   enumerate actual IDs/classes and render131 items under normal/6DoF and
   native/PS1 paths, including tints, all relevant transforms, tooltips and
   readable identifiers. Coordinate existing renderer ownership; this audit does
   not add a parallel renderer or certify appearance from a valid JSON file.
2. Map the six historical red-marked slots using matched executable/registry
   evidence. The old audit4 source/JAR remains unidentified; current order and
   five retired items are not a justified substitute. Retain original screenshots.
3. If extending tooling, add a small optional strict real-vanilla-JAR and
   registered-blockstate mode to existing audit_resources.py. Its current scan
   **explicitly defers vanilla dependencies**. A synthetic missing vanilla parent
   is recorded as external, while this audit's real-JAR resolver rejects it.
   That is a declared tool-coverage boundary, not evidence of a broken shipped
   model. No production audit-tool rewrite was included here.

Eleven pure synthetic controls pass: legitimate vanilla parents/variables/builtin
markers, missing vanilla/custom parents/textures, parent/override cycles and
unbound/cyclic texture variables. They modify only in-memory fixtures and own
temporary exported asset copies. No Java, save/registry edit, asset deletion,
central ledger transition, hardware/performance or whole S15 acceptance occurred.
