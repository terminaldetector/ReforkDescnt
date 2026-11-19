# S15-T01: six original red-marked inventory slots

Read-only source/image audit; no Java, registry/save edits, removals or deletion recommendations. Current consumer pin: **06d9c3860308058d3741813610bf771e353f7bd3**.

**The six slot identities can be reconstructed from the full historical creative matrix. They are not the five retired weapon IDs.** Original raw `image(9).png` (1280×720), SHA-256 **bc62ab754c2bd56b5dd2c53a57727b8f82f83e23d49a3a9c78a9d265b9370894**, is the marked image represented by panel08 of `INVENTORY_CONTACT_SHEET.jpg` (SHA e05b594f7f542f17e68af6ff2c17ef056f1bda13c8920c53c4df664f26d6d119). Both local images were compared byte-for-byte with their members in the original44-member v5 ZIP; neither is a recreated screenshot.

## Coordinates and exact source-backed IDs

Visible creative rows/columns are1-based, excluding the bottom hotbar. Raw-image slot centres are approximately x298+24×(column−1), y177+24×(row−1). Red marks are deliberately below/right of the item icon and sometimes overlap the next row; their centroid is not itself a slot centre.

| Mark/visible slot | Original red-marker centroid x,y | Original creative index,0-based | ID | Old model/icon evidence |
| --- | --- | --- | --- | --- |
| Upper group1; row3,col6 |422.85,235.18|86|**drmd:plasma_granite**|Item parent `drmd:block/plasma_granite`; cube_all using dark plasma_granite texture.|
| Upper group2; row3,col7 |447.76,234.85|87|**drmd:enclave_herald**|Item parent `drmd:block/enclave_herald`; cube_all with the light reactor_casing texture.|
| Upper group3; row3,col8 |471.12,235.30|88|**drmd:drill_rig**|Actual custom base/vertical drill-head block mesh, body drill_rig plus reactor_casing accent.|
| Upper group4; row3,col9 |494.89,235.14|89|**drmd:tunnel_drill_rig**|Actual low chassis/front-drill block mesh, tunnel_drill_rig body plus reactor_casing accent.|
| Lower group1; row5,col1 |306.07,287.26|99|**drmd:gravity_scanner**|Handheld model using `drmd:item/gravity_scanner`: cyan display on dark rectangular device.|
| Lower group2; row5,col2 |330.27,282.63|100|**drmd:cyclic_laser_kit**|Generated model layering vanilla minecart and redstone; the small red/black cart silhouette.|

Marker connected-component bounds respectively: (416,226)–(430,244), (440,227)–(455,243), (464,228)–(478,243), (487,227)–(503,243), (299,278)–(313,297), (323,275)–(338,290). These are measured original RGB pixels, not hand-drawn replacements.

## Why this is the correct historical matrix

Actual historical candidate **12d875dad165bc16111e2930f0e7997ffd273054** explicitly sets `mod_version=1.1.14-audit.4`. Its commit removed the three mirror-linker entries from this creative tab; they must not be inserted back into the screenshot reconstruction. The previous audit.3 order would shift the tail and does not match the image.

The tab contains exactly **14 base entries +40 `ArsenalCatalog.creativeWeapons` +14 spawn eggs +33 ModWorldBlocks additions =101 entries**. At the bottom of a9-column,5-visible-row creative grid,12 total rows imply seven scrolled rows: the visible first index is63. The last five spawn eggs are SEEKER/TRIPOD/SCANNER/SPIDER_TURRET/OBLIVION_SEEKER, matching the five visible green/pink/blue/yellow/purple eggs; SIX_D_SOIL then begins row1,col6. The33-entry world/tool suffix ends exactly at row5,col2 with the two marked tools. This matches the entire38-item visible matrix, not an isolated nearest-looking icon.

| Row | col1 | col2 | col3 | col4 | col5 | col6 | col7 | col8 | col9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|1|SEEKER egg|TRIPOD egg|SCANNER egg|SPIDER egg|OBLIVION egg|SIX_D_SOIL|HERMETIC_GATE|LASER_BARRIER|VOLUME_TURRET|
|2|LASER_TURRET|PLASMA_TURRET|POINT_DEFENSE_TURRET|MAGNETIC_ANOMALY|UNSTABLE_REACTOR|MIRROR|CHARGED_MIRROR|PORTAL_PANEL|PORTAL_GUN|
|3|GRAVITY_GENERATOR|GRAVITY_TORCH|LOCATOR_CORE|LOCATOR_RESONATOR|LOCATOR_PANEL|**PLASMA_GRANITE**|**ENCLAVE_HERALD**|**DRILL_RIG**|**TUNNEL_DRILL_RIG**|
|4|BUILD_TOOL|CONSTRUCTION_GREEN|CONSTRUCTION_YELLOW|CONSTRUCTION_BLUE|CONSTRUCTION_PURPLE|CONSTRUCTION_LASER|REPAIR_LASER|MINING_LASER|TUNNEL_LASER|
|5|**GRAVITY_SCANNER**|**CYCLIC_LASER_KIT**|empty|empty|empty|empty|empty|empty|empty|

Exact historical source anchors: `weapon/items/ModItems.java:283–303` supplies the base/weapon/egg entries; `weapon/registry/ArsenalCatalog.java:115+` supplies the40 weapon order; `entity/ModWorldBlocks.java:208–241` supplies the33 appended entries. The six marked entries are respectively lines227/228/229/230/240/241 in that exact source. The same exact historical file binds these fields to literal registry IDs; its complete registry/model source hashes are retained in the receipt. Current-source line numbers are not substituted for historical ones.

The source is a **matched audit.4 source candidate**. The original running audit.4 JAR hash was not supplied, so this does not invent a cryptographic binary/source link. Nevertheless the entire tab length, scroll position, five-egg anchor, complete block/tool sequence and model silhouettes agree. None of the six visual identities remains ambiguous within that matched matrix. User-save instances and unseen external modifications remain UNKNOWN.

## Current real use/save boundaries — all six KEEP

- **plasma_granite** remains a registered Block+BlockItem with real hard/breakable block settings. Existing LevelBuilder/Citadel source consumers use it; automatic legacy generation retirement does not make its saved block ID obsolete. No claim that those old generators should be reactivated.
- **enclave_herald** remains an actual EnclaveHeraldBlock interaction. EnclaveMissionSystem explicitly validates the loaded herald state for mission binding; IronGuildSurfaceGenerator also references it. World blocks and mission anchors are real compatibility consumers.
- **drill_rig** remains DrillRigBlock with real scheduled drilling and normal BlockState persistence; a recipe, `/ungravity` setup grant and construction/network references exist.
- **tunnel_drill_rig** remains TunnelDrillRigBlock with real scheduled drilling and BlockState persistence; a recipe and setup command grant exist. Its small icon is not proof of an empty implementation.
- **gravity_scanner** remains EngineerTools.GravityScannerItem: actual use samples current gravity/up/down and reports the field, with world particles. It has a real recipe and command grant.
- **cyclic_laser_kit** remains CyclicLaserKitItem.useOnBlock→RingDefenseStructures.placeCyclicLaserLoop (and sneak/full ring), consumes the kit outside Creative; LaserBarrierCartEntity uses the same item for drop compatibility. A real command grant exists. This audit does not accept that legacy construction path's safety/content parity or modify it.

These are source/use and normal save-identity edges, not proof that every item appears in the inspected user saves. Current root registrations/models/recipes and actual handler classes distinguish working objects from decorative placeholders. **No item/ID/model/recipe/save is removed or recommended for deletion.**

Machine-readable receipt: `s15-six-marked-matrix.json` в [архиве нативных материалов](EVIDENCE/round3-20261119/native-results.zip), SHA-256 **8faf51e029c0c21f941a07b86b6fc24b16584df3f86d2d23e8b2121e04addbd0**. It preserves all101 symbolic entries, the five-row matrix, original image hashes, historical model/source hashes and exact current consumer lines. This resolves the earlier “six red-marked slots UNKNOWN” inspection gap, while leaving broad S15 visual/runtime/use-save acceptance to its own criteria.
