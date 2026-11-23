package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Distinct inventory sprites and shared authored 3D geometry in held/ground display modes. */
public final class MissileItemRenderer {
    private MissileItemRenderer() {}

    public static void register() {
        MissileModel.register();
        register(ModItems.CONCUSSION, ProjectileVisual.ROCKET);
        register(ModItems.HOMING, ProjectileVisual.HOMING);
        register(ModItems.GUIDED, ProjectileVisual.GUIDED);
        register(ModItems.SMART_MISSILE, ProjectileVisual.SMART);
        register(ModItems.MEGA_MISSILE, ProjectileVisual.MEGA);
        register(ModItems.FLASH, ProjectileVisual.FLASH);
        register(ModItems.MERCURY, ProjectileVisual.MERCURY);
        register(ModItems.EARTHSHAKER, ProjectileVisual.EARTHSHAKER);
        register(ModItems.ROCKET_DUAL, ProjectileVisual.DUAL);
        register(ModItems.ROCKET_HEAVY, ProjectileVisual.HEAVY);
    }

    private static void register(Item item, ProjectileVisual visual) {
        BuiltinItemRendererRegistry.INSTANCE.register(item,
                (stack, mode, matrices, consumers, light, overlay) ->
                        render(visual, stack, mode, matrices, consumers, light, overlay));
    }

    private static void render(ProjectileVisual visual, ItemStack stack, ModelTransformationMode mode,
                               MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        var art = MissileModel.art();
        var model = art.variant(visual);
        if (model == null) return;
        if (mode == ModelTransformationMode.GUI) {
            VertexConsumer buffer = ItemRenderer.getDirectItemGlintConsumer(consumers,
                    RenderLayer.getEntityCutoutNoCull(model.icon()), true, stack.hasGlint());
            var entry = matrices.peek();
            spriteVertex(buffer, entry, 0, 0, 0, 1, light, overlay);
            spriteVertex(buffer, entry, 1, 0, 1, 1, light, overlay);
            spriteVertex(buffer, entry, 1, 1, 1, 0, light, overlay);
            spriteVertex(buffer, entry, 0, 1, 0, 0, light, overlay);
            return;
        }

        VertexConsumer buffer = ItemRenderer.getDirectItemGlintConsumer(consumers, art.layer(), true, stack.hasGlint());
        matrices.push();
        try {
            // The vanilla item renderer applies the item's display transform and subtracts its centre.
            matrices.translate(.5, .5, .5 - art.nozzleZ() * art.scale() * .5);
            matrices.scale(art.scale(), art.scale(), art.scale());
            if (model.pair()) {
                matrices.translate(-.76, 0, -.2);
                model.draw(matrices.peek(), buffer, light, overlay, -1);
                matrices.translate(1.52, 0, .4);
            }
            model.draw(matrices.peek(), buffer, light, overlay, -1);
        } finally {
            matrices.pop();
        }
    }

    private static void spriteVertex(VertexConsumer buffer, MatrixStack.Entry matrix, float x, float y,
                                     float u, float v, int light, int overlay) {
        buffer.vertex(matrix, x, y, .5f).color(0xFFFFFFFF).texture(u, v).overlay(overlay).light(light)
                .normal(matrix, 0, 0, 1);
    }
}
