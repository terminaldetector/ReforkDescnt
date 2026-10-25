package com.terminaldetector.drmd;

import com.terminaldetector.drmd.physics.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** NBT persistence without changing another concurrently running test's world profile. */
public final class PhysicsProfileGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitStandardSurvivesSavedWorldState(TestContext context) {
        var state=new PhysicsProfileState();state.set(PhysicsProfile.STANDARD);
        var nbt=state.writeNbt(new NbtCompound(),context.getWorld().getRegistryManager());
        var loaded=PhysicsProfileState.fromNbt(nbt,context.getWorld().getRegistryManager());
        context.assertTrue(loaded.profile()==PhysicsProfile.STANDARD,"explicit saved Standard does not become the Expanded default");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyStateUsesExpandedAndEachWorldStoresItsOwnChoice(TestContext context) {
        var a=PhysicsProfileState.fromNbt(new NbtCompound(),context.getWorld().getRegistryManager());
        var b=PhysicsProfileState.fromNbt(new NbtCompound(),context.getWorld().getRegistryManager());
        a.set(PhysicsProfile.STANDARD);
        context.assertTrue(b.profile()==PhysicsProfile.EXPANDED && a.profile()==PhysicsProfile.STANDARD,"no global/static profile leaks between saves");
        context.complete();
    }
}
