package com.terminaldetector.drmd;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;

/** Natural material cut, real child emission and a same-cut ordinary control. */
public final class PlayerAaOpaqueFractureGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-opaque-fracture",tickLimit=300)
    public void continuousOpaqueGunMaterialRetainsItsRefusalAfterActualFracture(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.FUTURE_FRAGMENT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-ordinary-fracture",tickLimit=300)
    public void releasedOrdinaryGunFragmentStillEmitsTwoRealMovingBolts(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.ORDINARY_FRAGMENT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-descendant-refusal",tickLimit=300)
    public void actualDescendantCutKeepsOneOriginalRefusalWithoutAncestryOrLiveClaims(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.FUTURE_DESCENDANT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-refusal-file-reject-independent",tickLimit=300)
    public void savedRefusalSurvivesRejectedEditAndOriginRemovalWithoutPausingIndependentGun(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.FUTURE_RELOAD_GUARD);}
}
