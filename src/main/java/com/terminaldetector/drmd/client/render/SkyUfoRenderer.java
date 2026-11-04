package com.terminaldetector.drmd.client.render;
import net.minecraft.client.render.entity.EntityRendererFactory;
/** The registered UFO uses its actual BlockBody geometry and quaternion, including holes and damage. */
public final class SkyUfoRenderer extends BlockBodyRenderer {
    public SkyUfoRenderer(EntityRendererFactory.Context context){super(context);}
}
