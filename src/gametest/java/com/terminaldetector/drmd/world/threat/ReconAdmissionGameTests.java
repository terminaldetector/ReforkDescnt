package com.terminaldetector.drmd.world.threat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** Three real default12000-online-tick scenarios; accelerated ticks, never DTO maturity. */
public final class ReconAdmissionGameTests implements FabricGameTest {
    private static void run(TestContext c,ReconAdmissionFixture.Scenario scenario){
        var fixture=new ReconAdmissionFixture(c,scenario);
        // Register exactly once, before task-map iteration; no nested scheduling.
        c.runAtEveryTick(()->{
            try{fixture.tick();}
            catch(RuntimeException|Error failure){
                fixture.failure(failure);
                try{fixture.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
                throw failure;
            }
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="timed-recon-observe",tickLimit=14_000)
    public void defaultSurvivalGraceThenOwnSightGivesFullWarningBeforeOneAssault(TestContext c){run(c,ReconAdmissionFixture.Scenario.OBSERVE);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="timed-recon-hidden",tickLimit=14_000)
    public void hiddenUnprovokedScoutDeathGivesCooldownWithoutRecursiveAlarm(TestContext c){run(c,ReconAdmissionFixture.Scenario.HIDDEN_INTERCEPT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="timed-recon-observed-intercept",tickLimit=14_000)
    public void observedScoutAndAssaultDeathsCannotMintAnotherCombatAllowance(TestContext c){run(c,ReconAdmissionFixture.Scenario.OBSERVED_INTERCEPT);}
}
