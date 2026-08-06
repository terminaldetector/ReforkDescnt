package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.screen.DescentSettingsScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pause-menu entry for DRMD 6DoF settings.
 *
 * <p>{@code ScreenEvents.AFTER_INIT + Screens.getButtons} is flaky with some loaders
 * (TLauncher skin overlays rebuild the menu). {@code addDrawableChild} at end of
 * {@code init} is the reliable path.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
	protected GameMenuScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void drmd$addSettingsButton(CallbackInfo ci) {
		// Top-right — bottom-left is often covered by launcher overlays / skin widgets.
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("menu.drmd.settings"),
						b -> {
							if (this.client != null) {
								this.client.setScreen(new DescentSettingsScreen(this));
							}
						})
				.dimensions(this.width - 128, 8, 120, 20)
				.build());
	}
}
