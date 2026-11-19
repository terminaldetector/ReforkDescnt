package com.terminaldetector.drmd.world.contraption.boarding;

import net.minecraft.item.ItemStack;
import java.util.UUID;

/** Native item operations, invoked once after its deferred physical move; never a second item tick. */
public interface LooseItemBodyAccess {
    boolean drmd$nativeCanMerge();
    void drmd$nativeTryMerge();
    void drmd$finishBodyMove();
    boolean drmd$canPickUpAs(UUID player);
    void drmd$mergeFreshUnownedDrop(ItemStack incoming);
}
