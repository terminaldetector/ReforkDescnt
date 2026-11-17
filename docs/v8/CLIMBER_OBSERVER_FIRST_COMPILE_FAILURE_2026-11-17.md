# First observer candidate: compile failed before any gameplay

Exact source `97aa3f9c8580e402b32b41f7bb6c5316a4f9f2ba`, executing helper SHA256 `c9c290aec203cdba9a8c3b83c861ae4236e579162420b7f88564aae77d6b5273`, ran the authorized first compile+same10 command once. **Compile failed, Gradle exit1 in16s; zero GameTests executed.** There is no fresh XML, new disposable world or new installable JAR. The prior932 gameplay remains5PASS/5FAIL, not a result from this run.

The two errors are in test-only `ClimberCornerTrace` lines42/61. The initializer `var arm=arm(actor)` receives a `SpiderEntity`, so Java selects public `void arm(SpiderEntity)` rather than private `Arm arm(LivingEntity)`. A void initializer cannot infer `var`. This is an observer source error, not gravity, navigation, lighting or fixture gameplay evidence. The minimal proposed correction is to rename only the private lookup and its two callers; no retry or correction occurred under this lease.

Before launch, source/main/original5/new3/assertions/targets/seed/budgets were frozen;1537 source/input identities and1375 prior class identities were saved. After the process closed, source/helper identity and zero live Java were verified and the lease returned immediately. Post-compile class identities are labeled as output of a failed compile, never a successfully executed current test build. The complete original first log/exit/source patch/helper/input and output receipts are retained in the archive below with per-payload SHA256 and ZIP CRC. No world or cache removal was needed.

Archive: [climber-observer-97aa3f9-first-compile.zip](EVIDENCE/climber-observer-97aa3f9-first-compile.zip), SHA256 `4004c68212da20bd535fc03786de3bab9b1f6e1868faa00a1d1a7571486b8407`, 238323bytes/18payloads, all hashes/CRC verified.
