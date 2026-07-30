package com.terminaldetector.drmd.client.input;

import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Binds matching COMMANDS.txt: H toggle, SHIFT dash, R alwaysrun, Q/E roll, etc.
 */
public final class DescentKeybinds {
	public static KeyBinding toggle;
	public static KeyBinding dash;
	public static KeyBinding alwaysRun;
	public static KeyBinding flightAssist;
	public static KeyBinding radar;
	public static KeyBinding rollLeft;
	public static KeyBinding rollRight;
	public static KeyBinding ascend;
	public static KeyBinding descend;
	public static KeyBinding hook;
	public static KeyBinding workshop;
	public static KeyBinding rocketMode;
	public static KeyBinding resetRoll;
	public static KeyBinding settings;
	public static KeyBinding weaponView;

	private static boolean dashQueued;
	private static boolean hookQueued;
	private static boolean wasEnabled;

	private DescentKeybinds() {}

	public static void register() {
		toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.category.drmd"));
		dash = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, "key.category.drmd"));
		alwaysRun = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.alwaysrun", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.drmd"));
		flightAssist = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.flightassist", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.category.drmd"));
		radar = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.radar", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, "key.category.drmd"));
		rollLeft = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.roll_left", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.category.drmd"));
		rollRight = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.roll_right", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.category.drmd"));
		ascend = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.ascend", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.category.drmd"));
		descend = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.descend", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.category.drmd"));
		hook = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.hook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.category.drmd"));
		workshop = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.workshop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.category.drmd"));
		rocketMode = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.rocket_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.drmd"));
		resetRoll = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.reset_roll", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.category.drmd"));
		settings = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.category.drmd"));
		weaponView = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.weapon_view", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.drmd"));
	}

	public static void tick(MinecraftClient client) {
		boolean en = DescentClientState.enabled;
		if (en && !wasEnabled && client.player != null) {
			ShipAttitudeClient.resetFromPlayer(client.player);
		}
		if (!en && wasEnabled) {
			ShipAttitudeClient.clear();
			DescentClientState.attitudeValid = false;
		}
		wasEnabled = en;

		while (toggle.wasPressed()) {
			boolean tab = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_TAB);
			if (tab && com.terminaldetector.drmd.client.hud.TerrainMap3d.canUse(client.player)) {
				com.terminaldetector.drmd.client.hud.TerrainMap3d.toggle();
			} else if (!tab) {
				// Explicit enable/disable keeps client prediction aligned with server mode.
				boolean next = !DescentClientState.enabled;
				DescentClientState.enabled = next;
				DescentClientState.footGravity = false;
				if (client.player != null) {
					var data = com.terminaldetector.drmd.DescentPlayerData.get(client.player);
					data.setEnabled(next);
					if (next) {
						com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(client.player);
						ShipAttitudeClient.resetFromPlayer(client.player);
						DescentClientState.attitudeValid = true;
					} else {
						data.setFlightVelocity(net.minecraft.util.math.Vec3d.ZERO);
						DescentClientState.attitudeValid = false;
						ShipAttitudeClient.clear();
					}
				}
				sendAction(next ? "enable" : "disable");
			}
		}
		while (dash.wasPressed()) {
			dashQueued = true;
			sendAction("dash");
			if (en && ShipAttitudeClient.isPrimed()) {
				com.terminaldetector.drmd.client.flight.DescentCamera.pulseDash(ShipAttitudeClient.get().forward());
			}
		}
		while (alwaysRun.wasPressed()) sendAction("alwaysrun");
		while (flightAssist.wasPressed()) sendAction("flightassist");
		while (radar.wasPressed()) sendAction("radar");
		while (rocketMode.wasPressed()) sendAction("rocket_next");
		while (resetRoll.wasPressed()) {
			if (client.player != null && en) {
				ShipAttitudeClient.level();
			}
			sendAction("reset_roll");
		}
		while (workshop.wasPressed()) DescentClient.openWorkshop();
		while (settings.wasPressed()) DescentClient.openSettings();
		while (weaponView.wasPressed()) {
			DescentClientState.weaponViewMode = DescentClientState.weaponViewMode.next();
			if (client.player != null) {
				client.player.sendMessage(net.minecraft.text.Text.literal(
						"§bWeapon view §f" + DescentClientState.weaponViewMode.label()
								+ " §8· MMB=Use · Alt+MMB=ракеты"), true);
			}
		}
		if (hook.isPressed()) hookQueued = true;

		if (en && client.player != null) {
			float rollIn = 0;
			if (rollLeft.isPressed()) rollIn -= 1;
			if (rollRight.isPressed()) rollIn += 1;
			float dt = 1f / 20f;
			ShipAttitudeClient.tickRoll(client.player, rollIn, dt);
			com.terminaldetector.drmd.client.flight.DescentCamera.tick(client.player, dt);
		}
		WeaponUseClient.tick(client);
		com.terminaldetector.drmd.client.hud.TerrainMap3d.tick(client);
	}

	public static void sendInput(MinecraftClient client) {
		if (client.player == null) return;
		float forward = 0, strafe = 0, vertical = 0, roll = 0;
		if (client.options.forwardKey.isPressed()) forward += 1;
		if (client.options.backKey.isPressed()) forward -= 1;
		if (client.options.leftKey.isPressed()) strafe += 1;
		if (client.options.rightKey.isPressed()) strafe -= 1;
		// GMod IN_JUMP / IN_DUCK → ship local up/down (not world Y).
		// Jump key shares Space with ascend; do NOT use sneakKey (Shift = dash).
		if (ascend.isPressed() || client.options.jumpKey.isPressed()) vertical += 1;
		if (descend.isPressed()) vertical -= 1;
		long win = client.getWindow().getHandle();
		if (InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			vertical -= 1;
		}
		vertical = Math.max(-1f, Math.min(1f, vertical));
		if (rollLeft.isPressed()) roll -= 1;
		if (rollRight.isPressed()) roll += 1;

		boolean dash = dashQueued;
		boolean hk = hookQueued;
		dashQueued = false;
		hookQueued = false;

		boolean att = DescentClientState.enabled && DescentClientState.attitudeValid;
		net.minecraft.util.math.Vec3d fwd = att
				? new net.minecraft.util.math.Vec3d(DescentClientState.attFx, DescentClientState.attFy, DescentClientState.attFz)
				: client.player.getRotationVec(1f);
		net.minecraft.util.math.Vec3d up = att
				? new net.minecraft.util.math.Vec3d(DescentClientState.attUx, DescentClientState.attUy, DescentClientState.attUz)
				: new net.minecraft.util.math.Vec3d(0, 1, 0);
		// Predict locally so creative/SP does not wait a server round-trip (feels frozen otherwise).
		com.terminaldetector.drmd.flight.FlightMotion.clientPredict(
				client.player, forward, strafe, vertical, fwd, up);

		ClientPlayNetworking.send(new ModNetworking.InputPayload(
				forward, strafe, vertical, roll, dash, hk,
				att,
				att ? DescentClientState.attFx : 0,
				att ? DescentClientState.attFy : 0,
				att ? DescentClientState.attFz : 1,
				att ? DescentClientState.attUx : 0,
				att ? DescentClientState.attUy : 1,
				att ? DescentClientState.attUz : 0
		));
	}

	private static void sendAction(String action) {
		ClientPlayNetworking.send(new ModNetworking.ActionPayload(action));
	}
}
