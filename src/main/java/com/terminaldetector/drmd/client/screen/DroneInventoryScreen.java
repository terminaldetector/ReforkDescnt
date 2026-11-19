package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.render.DroneLoadoutPreview;
import com.terminaldetector.drmd.inventory.DroneInventoryHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class DroneInventoryScreen extends HandledScreen<DroneInventoryHandler> {
    private static final Text[] LABELS = {Text.translatable("container.drmd.drone_inventory.primary"),
            Text.translatable("container.drmd.drone_inventory.secondary"), Text.translatable("container.drmd.drone_inventory.auxiliary"),
            Text.translatable("container.drmd.drone_inventory.reserve"), Text.translatable("container.drmd.drone_inventory.engine")};
    private DroneLoadoutPreview preview;
    private int previewBank;

    public DroneInventoryScreen(DroneInventoryHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176; backgroundHeight = 212; playerInventoryTitleY = 114;
    }

    @Override protected void init() {
        super.init();
        preview = new DroneLoadoutPreview();
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        for (int i = 0; i < 5; i++) {
            var slot = handler.getSlot(i);
            int slotX = x + slot.x;
            int slotY = y + slot.y;
            if (mouseX >= slotX - 2 && mouseX < slotX + 18 && mouseY >= slotY - 12 && mouseY < slotY + 18)
                previewBank = i;
        }
        context.fill(x, y, x + 176, y + 212, 0xee18252e);
        context.fill(x + 4, y + 18, x + 172, y + 79, 0xff0d1721);
        preview.draw(context, handler.getSlot(previewBank).getStack(), previewBank, x + 4, y + 29, 168, 49);
        context.drawText(textRenderer, Text.translatable("container.drmd.drone_inventory.preview", LABELS[previewBank]), x + 8, y + 20, 0xa5d8db, false);

        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18, slotY = y + (row == 3 ? 184 : 126 + row * 18);
            context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xff647987);
            context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xff101c25);
        }
        for (int i = 0; i < 5; i++) {
            int slotX = x + handler.getSlot(i).x;
            context.fill(slotX - 1, y + 90, slotX + 17, y + 108, i == previewBank ? 0xff63d8dc : 0xff80929c);
            context.fill(slotX, y + 91, slotX + 16, y + 107, 0xff26353d);
            Text label = LABELS[i];
            float scale = Math.min(1.0f, 30.0f / Math.max(1, textRenderer.getWidth(label)));
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(slotX + 8, y + 80, 0);
            matrices.scale(scale, scale, 1.0f);
            context.drawCenteredTextWithShadow(textRenderer, label, 0, 0, i == previewBank ? 0xe0ffff : 0xa5d8db);
            matrices.pop();
        }
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 8, 6, 0xe0edf4, false);
        context.drawText(textRenderer, playerInventoryTitle, 8, 114, 0xa5becb, false);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        for (int bank = 0; bank < LABELS.length; bank++) {
            var slot = handler.getSlot(bank);
            if (!isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) continue;
            // Vanilla puts its tooltip just above this anchor. Keep that background above
            // the equipment captions even when the pointer is centered over a socket.
            int tooltipY = Math.min(mouseY, y + 79);
            if (slot.hasStack()) drawMouseoverTooltip(context, mouseX, tooltipY);
            else context.drawTooltip(textRenderer, socketHint(bank), mouseX, tooltipY);
            return;
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private Text socketHint(int bank) {
        return switch (bank) {
            case 0 -> Text.translatable("container.drmd.drone_inventory.primary.hint", client.options.attackKey.getBoundKeyLocalizedText());
            case 1 -> Text.translatable("container.drmd.drone_inventory.secondary.hint", client.options.useKey.getBoundKeyLocalizedText());
            case 2 -> Text.translatable("container.drmd.drone_inventory.auxiliary.hint", DescentKeybinds.auxiliary.getBoundKeyLocalizedText());
            case 3 -> Text.translatable("container.drmd.drone_inventory.reserve.hint");
            default -> Text.translatable("container.drmd.drone_inventory.engine.hint", DescentKeybinds.alwaysRun.getBoundKeyLocalizedText());
        };
    }
}
