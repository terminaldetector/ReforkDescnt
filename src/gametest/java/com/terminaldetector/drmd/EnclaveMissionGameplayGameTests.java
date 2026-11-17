package com.terminaldetector.drmd;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** Actual dispatcher, movement, ordinary mining and Herald extraction; no protocol mutation. */
public final class EnclaveMissionGameplayGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-mission-lab",tickLimit=500)
    public void labInfiltrationRequiresActualObjectivesAndOneExtraction(TestContext context){run(context,EnclaveMissionGameplayFixture.Case.LAB);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-mission-side",tickLimit=500)
    public void secondDataContractRequiresItsDistinctFinalEntry(TestContext context){run(context,EnclaveMissionGameplayFixture.Case.SIDE);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-mission-foreign",tickLimit=500)
    public void foreignMiningAndMissingPanelCannotAdvanceOwner(TestContext context){run(context,EnclaveMissionGameplayFixture.Case.FOREIGN);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-mission-rights",tickLimit=500)
    public void actualModesAndForeignOwnersCannotClaimAnotherInstance(TestContext context){run(context,EnclaveMissionGameplayFixture.Case.RIGHTS);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-mission-invalidation",tickLimit=500)
    public void wrongContactAndReplacementCannotInheritReadyClaim(TestContext context){run(context,EnclaveMissionGameplayFixture.Case.INVALIDATE);}
    private static void run(TestContext context,EnclaveMissionGameplayFixture.Case scenario){
        var driver=new EnclaveMissionGameplayFixture(context,scenario);
        context.runAtEveryTick(()->{
            if(driver.finished)return;
            try{driver.tick();}
            catch(RuntimeException|Error failure){driver.finished=true;driver.close(failure);throw failure;}
        });
    }
}
