# Combined server increment and remaining native failures

Exact source `bd29aab11f6fcba71f5234b64616d2284bf0aec8` passes **803 unit tests,
406 ordinary GameTests and the complete 64-entrypoint manifest**, with no failures,
errors or skips. The full build/remap takes 5m12s. Freshness checks exclude a
preceding GameTest XML; the actual executing runner is preserved.

Game JAR SHA-256: `5160df549cab77b53f0f09873f333ffda0b176ab29ce759bfe523a322250e4dd`.
Observer SHA-256: `a456314bd97b610dfbca379b36344e80d83c8f677f6d6b3c4ab58fabb2893da0`.

The [server evidence archive](EVIDENCE/integrated-bd29aab-server.zip) contains
19 payloads plus their SHA-256 manifest, including full logs/XML, unit XML bundle,
processed manifest, source/build receipts, report freshness and the explicitly
derived native-launch receipt. All payload hashes and ZIP CRCs were verified.
Archive SHA-256: `354f1ed00953b51f814174e18245976eb2eb3854199e48fe69d55d6345cf19cd`.
See the [parsed receipt](EVIDENCE/integrated-bd29aab-server.json).

This ordinary full suite uses the stock backend with TestServer's discoverable
packs, including the legacy Advanced registry profile. [Actual saved metadata](EVIDENCE/integrated-bd29aab-world-profile.json)
is retained; no default-Vanilla-height acceptance is inferred. The two dedicated
surface tests explicitly select normal packs before registry loading, require
actual -64/320 bounds and pass separately. They are **not** in the ordinary406.
The eight legacy tall-registry callback checks also remain separate.

New integrated behavior beyond the earlier699 checkpoint:

- Genuinely submerged FishEntity bypasses standing mounting/damping while retaining
  native water travel. The same eight tests pass after a retained two-failure
  negative. This is not complete aquatic orientation/navigation or C1–C7 acceptance.
- Surface ingestion samples the actual heightmap block and stores its physical
  top face. Five boundary units and two real normal-profile callback tests pass.
- The deterministic manual-city blueprint, durable journal and explicit owner
  controller pass26 pure JDK controls and now compile in the real mod. There is
  no registered runtime city adapter, authored world or city destruction proof.
- The opt-in observer records real frame/tick/heap spans. Its separate enabled
  smoke is preserved with its original wrapper failure; no benchmark acceptance.

One actual BCC/Sodium GL3.3 client used **these same full-tested JARs**. Its loaded
GOLD → demolition/LIME → restored GOLD controls pass through the live server
store, flushed/read-back disk and received packet. A second, distinct defect then
fails the client with exit255: after20 stable observations of an unavailable high
cube and a retained readable low cube, a low edit re-ingests the cached high winner.
Its unavailable block is read as AIR, replacing known GOLD colour16445005 with0
in server, disk and packet while height2301 remains. This is a retained **native
failure**, not an overall client PASS or terrain-smoothing proof. The next fix
must guard actual winner availability and preserve the observed cell.

Separate ongoing branches preserve real short-click input loss (press+release in
one server tick), sky-envelope clipping/overwriting LOD, climber admission and city
protocol extensions. They are not silently included in this tested source.
The earlier699 presentation/mechanism native passes keep their own source/JARs
and [report](MULTI_AGENT_INTEGRATION_2026-11-17.md).

Closed full-run world:1214files,167769367bytes, verified archive22089503bytes,
SHA-256 `7453a4d7a72cdaa91b3f95cac6810139d823a2ab0cc2969eb17f60cad7419409`,
retained at `work/ufo-body/integrated-bd29aab.zip`. Only its disposable closed copy
was reclaimed after per-file/CRC verification; archival preservation is not a
two-process gameplay restore proof. Full specifications, performance, multiplayer
and release acceptance remain open. No publication or GitHub action occurred.
