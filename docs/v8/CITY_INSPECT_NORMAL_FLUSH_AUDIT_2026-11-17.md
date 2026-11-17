# Inspect feedback completion: actual flush audit and one-tick fixture correction

First463packet evidence is sealed0960b2f. No production correction follows: actual
GameMessageS2CPacket already contained correct anchor/counts/sterile text. Actual
player source, feedback rule, chatFULL and same open/writable embedded connection
were confirmed. Raw delivered queue stayed31GameMessages before/after immediate
write. This is observed pending completion, not a guessed silent/hidden source.

Read-only Python decoded the exact Minecraft and Netty jars recorded by that
executing run. Current full jar hashes equal actual classpath hashes; no Java or
class loading was launched. Reproducible reader/output/provenance are included.
This reads cached class bodies; runtime event injections are not relabeled a fresh
decompilation of transformed in-memory classes.

## Exact policy

- ServerPlayNetworkHandler inherits ServerCommonNetworkHandler.sendPacket/send.
  Common.disableFlush sets flushDisabledtrue. Common.send passes flushfalse to
  ClientConnection when flushDisabled and server.isOnThread are bothtrue.
- MinecraftServer.tickWorlds starts player batching through method_53501 invoking
  handler.disableFlush, runs world logic containing this GameTest callback, then
  its final player loop invokes handler.enableFlush (bytecode offset368).
- Common.enableFlush clears the flag and calls ClientConnection.flush.
- ClientConnection.sendInternal(false) calls channel.write; true selects
  writeAndFlush. Its flush path calls channel.flush directly for the actual
  in-event-loop caller (which463measuredtrue).
- EmbeddedChannel.doWrite takes flushed ChannelOutboundBuffer.current objects,
  then handleOutboundMessage appends them to outboundMessages. Its optional
  flushOutbound helper first runs pending tasks and flushes; our463event/connection
  pending queues were0, and vanilla already supplies the normal end-tick flush.

The immediate oracle was inside world-tick batching, before normal completion.
Failed cleanup closed the channel before allowing that end-tick path to complete,
explaining the observed subsequent Can't deliver callback without requiring a
gameplay message change.

## Root-approved single test-only correction

Persist the original raw GameMessage offset and unchanged expected marker/sterile
tokens with actual issued game/server tick. Execute the **same original content
predicate once on the next existing fixture callback**, after normal MinecraftServer
end-tick enableFlush. Require/document actual issue→receipt game/server delta1;
do not retry if it is absent. Existing optional cadence also enforces consecutive
actual counters. Log the next-frame raw packet count and connection state.

This permits one normal server flush boundary, with no manual channel.flush,
flushOutbound, runPendingTasks, sleep/yield, new event/callback, packet injection or
console source substitution. A next-frame failure remains a real first result.
flushDisabled can be true again at the next world's tick because a new batching
cycle has begun; the already completed prior packet must still be in the raw queue.

Move only this observation timing. Keep all31 original assertion texts/predicates,
original command order and three inert ticks after dispatch. Keep runtime300
including this next tick, setup10s/abort5s/framework30350/180+10 and every ownership/
state/files/CAS/WAL check. No counter reset or additional timeout. Main/hook remain
unchanged; later unmasked activation still requires actual command PASS first.

## Preservation and reclaim

Root authorized reclaim of seven own closed raw test worlds after full archives,
all member/raw SHAs, CRC and exclusive POSIX fcntl.lockf(r+b) session.lock rechecks.
Exact original paths→archive hashes and file counts are retained in
CITY_CLOSED_RAW_RECLAIM_2026-11-17.json. Only those raw disposable-world directories
were removed; all logs/proofs/payload/source/libs and archives remain. Older flock
guards were augmented by the Java FileLock-compatible lockf verification before
deletion. No active save or reader/donor was reclaimed.

Audit components: CITY_SEND_FLUSH_CLASS_READER_2026-11-17.py,
CITY_SEND_FLUSH_BYTECODE_2026-11-17.txt and CITY_SEND_FLUSH_PROVENANCE_2026-11-17.json.
The exact fixture timing correction is a separate source commit after this audit.
