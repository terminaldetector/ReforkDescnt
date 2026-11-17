# Native gravity observer: deferred-use ordering correction

This is an observer-only correction, separate from the server weapon fix.
Original `07b2fa8` source/helper bytes and its first failed real-client run remain
preserved in `EVIDENCE/native-gravity-input-short-click-07b.zip` and its report.
The original actual one-client-tick key pulses are retained unchanged.

The original observer required a decoded release sequence **after** the actual
`Item.use` callback. The captured native `07b` trace already demonstrates that
a real short press and release can both reach the server in one world tick.
The approved server latch intentionally defers their item action until the
arsenal simulation tick. A correct action can therefore follow its physical
release, and that ordering must not be mistaken for a stuck held button.

The observer now correlates each physical pulse using its own pre-pulse trace
baseline. It requires an actual eligible decoded press after that baseline,
the matching scene's actual item use after that press, an actual eligible
release after the press, and the latest decoded input in the scene to remain
eligible with mask zero. It stores the original press/release/latest events
in each scene's capture/throw receipt. A separate throw baseline also prevents
an unrelated prior item callback from satisfying the second physical button.

The real item may run before or after its release packet. This does not replace
the driver with a sustained press, invoke an item or receiver directly, invent
a held state, or relax capture/throw identity, exact energy cost, real cooldown,
eight normal hold ticks, body movement, release-to-zero, GUI selection, empty
flight hand, frame freshness or the unchanged transaction/global deadlines.

This contract correction is based on causal input ordering, not a claim that a
new native run has passed. Stock server-route GameTests do not exercise this
observer; native compilation and all six real-client scenes require a separate
fresh execution and retained first result.
