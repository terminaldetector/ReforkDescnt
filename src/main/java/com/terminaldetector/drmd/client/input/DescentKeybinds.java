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
 * Binds: H toggle, SHIFT dash, R hold afterburner (Descent cruise), Q/E roll, etc.
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
	public static KeyBinding customize;
	public static KeyBinding rocketMode;
	public static KeyBinding resetRoll;
	public static KeyBinding settings;

	private static boolean dashQueued;
	private static boolean hookQueued;
	private static boolean wasEnabled;

	/** Call on disconnect so the next join sees a clean false→true edge. */
	public static void resetSession() {
		wasEnabled = false;
		dashQueued = false;
		hookQueued = false;
		lastForward = lastStrafe = lastVertical = 0;
		inputSendAge = 0;
	}
	/**
	 * Thrust axes as of the last sample.
	 *
	 * <p>Sampled on demand rather than cached from the end-of-tick send: {@code travel} runs during
	 * the world tick and the input packet goes out after it, so reading a stored value there would
	 * fly the ship on last tick's stick.
	 */
	private static float lastForward, lastStrafe, lastVertical;

	public static float inputForward() { return lastForward; }
	public static float inputStrafe() { return lastStrafe; }
	public static float inputVertical() { return lastVertical; }

	/** Read the thrust axes from the keyboard now. Both the integrator and the packet use this. */
	public static void sampleThrust() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) return;
		float forward = 0, strafe = 0, vertical = 0;
		if (client.options.forwardKey.isPressed()) forward += 1;
		if (client.options.backKey.isPressed()) forward -= 1;
		if (client.options.leftKey.isPressed()) strafe += 1;
		if (client.options.rightKey.isPressed()) strafe -= 1;
		if (ascend.isPressed()) vertical += 1;
		if (descend.isPressed()) vertical -= 1;
		lastForward = forward;
		lastStrafe = strafe;
		lastVertical = vertical;
	}

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
		customize = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.customize", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.category.drmd"));
		rocketMode = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.rocket_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.drmd"));
		resetRoll = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.reset_roll", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.category.drmd"));
		settings = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, "key.category.drmd"));
	}

	public static void tick(MinecraftClient client) {
		boolean en = DescentClientState.enabled;
		if (en && !wasEnabled && client.player != null) {
			ShipAttitudeClient.resetFromPlayer(client.player);
		}
		if (!en && wasEnabled) {
			ShipAttitudeClient.clear();
			com.terminaldetector.drmd.client.flight.DescentFlightMotion.clear();
			DescentClientState.attitudeValid = false;
		}
		wasEnabled = en;

		while (toggle.wasPressed()) {
			boolean tab = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_TAB);
			if (tab && com.terminaldetector.drmd.client.hud.TerrainMap3d.canUse(client.player)) {
				com.terminaldetector.drmd.client.hud.TerrainMap3d.toggle();
			} else if (!tab) {
				sendAction("toggle");
			}
		}
		while (dash.wasPressed()) {
			dashQueued = true;
			sendAction("dash");
			if (en && ShipAttitudeClient.isPrimed()) {
				com.terminaldetector.drmd.client.flight.DescentCamera.pulseDash(ShipAttitudeClient.get().forward());
			}
		}
		// Afterburner is hold-R via InputPayload — no toggle edge here.
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
		while (customize.wasPressed()) DescentClient.openShipCustomize();
		while (settings.wasPressed()) DescentClient.openSettings();
		if (hook.isPressed()) hookQueued = true;

		if (en && client.player != null) {
			float rollIn = 0;
			if (rollLeft.isPressed()) rollIn -= 1;
			if (rollRight.isPressed()) rollIn += 1;
			// Stick only — basis advances on the render clock so barrel rolls are not 20 Hz stepped.
			ShipAttitudeClient.setRollInput(rollIn);
			float dt = 1f / 20f;
			com.terminaldetector.drmd.client.flight.DescentCamera.tick(client.player, dt);
		} else {
			ShipAttitudeClient.setRollInput(0);
		}
		com.terminaldetector.drmd.client.hud.TerrainMap3d.tick(client);
	}

	private static int inputSendAge;
	private static float lastSentFx, lastSentFy, lastSentFz, lastSentUx, lastSentUy, lastSentUz;
	private static float lastSentFwd, lastSentStrafe, lastSentVert, lastSentRoll;
	private static boolean lastSentAfterburner;

	public static void sendInput(MinecraftClient client) {
		if (client.player == null) return;
		sampleThrust();
		float forward = lastForward, strafe = lastStrafe, vertical = lastVertical;
		float roll = 0;
		if (rollLeft.isPressed()) roll -= 1;
		if (rollRight.isPressed()) roll += 1;

		boolean dash = dashQueued;
		boolean hk = hookQueued;
		boolean afterburner = alwaysRun.isPressed();
		dashQueued = false;
		hookQueued = false;

		boolean att = DescentClientState.enabled && DescentClientState.attitudeValid;
		float fx = att ? DescentClientState.attFx : 0;
		float fy = att ? DescentClientState.attFy : 0;
		float fz = att ? DescentClientState.attFz : 1;
		float ux = att ? DescentClientState.attUx : 0;
		float uy = att ? DescentClientState.attUy : 1;
		float uz = att ? DescentClientState.attUz : 0;

		inputSendAge++;
		boolean stickChanged = Math.abs(forward - lastSentFwd) > 0.01f
				|| Math.abs(strafe - lastSentStrafe) > 0.01f
				|| Math.abs(vertical - lastSentVert) > 0.01f
				|| Math.abs(roll - lastSentRoll) > 0.01f;
		boolean attChanged = Math.abs(fx - lastSentFx) > 0.002f || Math.abs(fy - lastSentFy) > 0.002f
				|| Math.abs(fz - lastSentFz) > 0.002f || Math.abs(ux - lastSentUx) > 0.002f
				|| Math.abs(uy - lastSentUy) > 0.002f || Math.abs(uz - lastSentUz) > 0.002f;
		boolean abChanged = afterburner != lastSentAfterburner;
		// Keep a heartbeat so the server does not go stale, but skip duplicate packets mid-coast.
		if (!dash && !hk && !abChanged && !stickChanged && !attChanged && inputSendAge < 4) return;
		inputSendAge = 0;
		lastSentFwd = forward;
		lastSentStrafe = strafe;
		lastSentVert = vertical;
		lastSentRoll = roll;
		lastSentAfterburner = afterburner;
		lastSentFx = fx;
		lastSentFy = fy;
		lastSentFz = fz;
		lastSentUx = ux;
		lastSentUy = uy;
		lastSentUz = uz;

		// Local prediction: afterburner state before the sync round-trip.
		DescentClientState.alwaysRun = afterburner && DescentClientState.enabled;

		ClientPlayNetworking.send(new ModNetworking.InputPayload(
				forward, strafe, vertical, roll, dash, hk, afterburner,
				att, fx, fy, fz, ux, uy, uz
		));
	}

	private static void sendAction(String action) {
		ClientPlayNetworking.send(new ModNetworking.ActionPayload(action));
	}
}
