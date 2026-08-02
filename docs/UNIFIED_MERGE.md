# Unified merge (1.1.2)

All draft feature branches are merged into `cursor/megacity-complex-02fc`:

| Branch | PR | Status |
|--------|----|--------|
| `cursor/ordnance-cluster-rockets-02fc` | #3 | already ancestor |
| `cursor/descent-laser-bolts-02fc` | #5 | merged (jar-only tip) |
| `cursor/arsenal-descent-closed-02fc` | #6 | merged (jar-only tip) |
| `cursor/afterburner-world-layers-02fc` | #7 | merged (jar-only tip) |
| `cursor/release-audit-02fc` | #4 | already ancestor |
| `cursor/minecraft-mod-port-02fc` | #1 | already ancestor |

**One jar:** `dist/drmd-6dof-1.1.2-fabric-1.21.1.jar`

Includes: closed Descent arsenal, dual-bolt lasers, rockets/bombs, afterburner,
world layers / SeamWarmup, Spark+Starlink ring, Klondike islands, 6DoF lock.

**End band restored (1.1.2).** Archipelago + reactor arena at the top of the column —
see [`END_BAND.md`](END_BAND.md).

**Voxel LLOD deleted (1.1.2).** 1.1.1 switched the pipeline off and left it compiled; the packages,
payloads and client renderers are gone now. Far view → [Distant Horizons](https://modrinth.com/mod/distanthorizons) (+ Sodium).

Creative: DRMD weapons tab · `/d6 weapons give_all` · `/d6 kit`
