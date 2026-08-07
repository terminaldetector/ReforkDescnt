package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.screen.DrmdWorldGenScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "DRMD World Generation" entry on the vanilla Create World screen — same {@code addDrawableChild} at
 * end of {@code init} technique as {@link GameMenuScreenMixin}, for the same reason: it is the one
 * that survives third-party launcher overlays rebuilding the menu (see that class's doc comment).
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
	protected CreateWorldScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void drmd$addWorldGenButton(CallbackInfo ci) {
		// Top-right, same corner GameMenuScreenMixin uses — clear of vanilla's centred column of
		// world-creation buttons and of the same launcher overlays that motivated that placement.
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("menu.drmd.worldgen"),
						b -> {
							if (this.client != null) {
								this.client.setScreen(new DrmdWorldGenScreen(this));
							}
						})
				.dimensions(this.width - 128, 8, 120, 20)
				.build());
	}
}
