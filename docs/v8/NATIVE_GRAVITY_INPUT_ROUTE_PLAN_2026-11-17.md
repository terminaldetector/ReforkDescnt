# Native gravity-item input route: approved single observer

Base f3d97844b2335d8fb9700960a12377fd8d3c2ca9. Branch experiment/gravitygun-native-input-20261117. Source only until root grants the sole Java/native slot; root centralizes new mixin registration. No production input/physics/loadout source changes and no direct calls to trigger receiver, item use, capture or fling from the observer.

One actual integrated native client runs six transactions: SWARM/RAIL/SHIFT in ordinary mode (normal hotbar key selection, alternating LMB/RMB), then the same modes in 6DoF (H via real binding, N equipment UI/normal slot clicks; empty main hand proves primary/secondary sockets). Own fixture bodies may be created/retired only between completed transactions with held count zero, maximum four live bodies and100cells in the heavy target. No follow teleport or state/energy rewriting during capture/hold/throw.

Input edges use actual KeyBinding edge+held state; release sends normal mask0. Read-only probes record server trigger receipt, selected-slot/click-slot packets and actual item-use pre/post state/energy. The observer waits for release0, eight ordinary server ticks, real cooldown expiry and client held target sync before the second key pulse. The real item-use callback must capture then throw the original identities, with exact synchronous costs; natural regeneration is observed between calls.

Budget committed BEFORE observer source:
- Actual production regenGravityTick adds0.5 capacitor per normal20Hz tick, max100. Capture+throw budgets are28 SWARM(4),28 RAIL(100mass),38 SHIFT. From zero, largest admission needs76ticks. Admission limit96ticks per transaction; no test recharge injection.
- Initial native/load bootstrap600server ticks; mode-toggle60.
- Each of6 stages allows selection120, energy admission96, fixture/cube synchronization100, transaction160 server ticks.
- Worst sum600 +6*(120+96+100+160)+60+40finalization =3556 server ticks. Total server limit3600ticks; independent wall-clock limit300seconds from observer registration. All188 total energy versus default100 needs at most176regeneration ticks absent intervening regen, below aggregate576tick energy admission allowance. Client-tick fallback12000; no unbounded waits.
- Body creation may wait for existing loaded collision halo; no raw ray/chunk generation used as admission. Screens, packet order and ownership are checked; failure writes fresh stage/trace/frame and releases all test key states.

Limits: six transactions in one script, not six processes. Real buttons/cooldown/normal selection only; zero-G body launch is not material-hit/compensated ballistics or restart/performance closure. Frame evidence is fresh world/UI proof, not PS1 pixel-quality acceptance. No common FrameProbe/CubicFrameProbe/CubicClientGate or PS1 edits; own property bootstrap and WorldRenderEvents callbacks only.


Implementation readiness (source only):
- New native observer/trace and four owned mixins only. Source registration remains root-owned; append GravityInputRouteBootstrapProbe, GravityInputTriggerProbe, GravityInputItemUseProbe, GravityInputSelectionProbe to src/cubiccheck/resources/drmd.cubiccheck.mixins.json.
- Property drmd.gate.gravityInputRoute; required output gravity-input-route-ok.json. Missing output/any failed stage is not a pass.
- One START_CLIENT_TICK state machine; actual bound-key edge+pressed state for hotbar/H/N/LMB/RMB. Handled-screen mouse press/release and Escape use normal client screen methods and packet acknowledgments. Ordinary mode is6DoF-off in a creative controlled lab, not Survival balance acceptance.
- Bootstrap only: initial player pose/creative hover, normal inventory provision and loaded49-block floor. No equipment socket writes; old socket contents are returned through UI clicks. After startup the only actor controls are real keys, and all target creation/retirement requires held0 between transactions.
- Real use probes are read-only before/return, exposing resource, actual manager cooldown, selected item, original target identity/mass/position/velocity and own shifter field. Both failed pulse sampling and incorrect selection leave bounded raw trace instead of observer repairs.
- Transaction160 includes capture, normal8tick hold, release0, real cooldown expiry, throw, three held/thrown world frames and retirement. Retirement has no additional unbudgeted stage window. Finalization40 is global; documented worst3556 stays below total3600.
- WorldRenderEvents.END supplies fresh world frames with no shared FrameProbe/PS1 edits. Equipment handler synchronization proves UI selection; no UI pixel-quality claim.
- First compile/runtime remains pending root's explicit Java lease. No native result or gameplay bug is claimed from source.
