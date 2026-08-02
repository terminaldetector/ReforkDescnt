package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.render.ModelOrientation;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pilot's model has to end up in the ship's orientation, nose included.
 *
 * <p>The shortest-arc version of this was exact for a pitch alone and exact for a roll alone but
 * wrong for both together — at 30 degrees nose-up and 180 of bank the model faced backwards. These
 * sweep the combinations that caught it.
 */
class ModelOrientationTest {
	/** Undo the turn setupTransforms has already applied, to see where the model really points. */
	private static Vector3f throughStack(Quaternionf q, float bodyYaw, Vector3f modelAxis) {
		Vector3f v = new Vector3f(modelAxis);
		q.transform(v);
		return v.rotateY((float) Math.toRadians(180.0 - bodyYaw));
	}

	private static Vec3d look(double yawDeg, double pitchDeg) {
		double y = Math.toRadians(yawDeg);
		double p = Math.toRadians(pitchDeg);
		return new Vec3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	private static double angleBetween(Vector3f a, Vec3d b) {
		Vector3f n = new Vector3f(a).normalize();
		double dot = n.x * b.x + n.y * b.y + n.z * b.z;
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
	}

	@Test
	@DisplayName("model up and nose both land on the ship basis")
	void reproducesTheWholeBasis() {
		double worstUp = 0;
		double worstNose = 0;
		for (int yaw = -180; yaw <= 180; yaw += 17) {
			for (int pitch : new int[]{-85, -60, -30, -5, 0, 5, 30, 60, 85}) {
				for (int bank : new int[]{-175, -135, -90, -45, 0, 45, 90, 135, 175}) {
					ShipAttitude att = new ShipAttitude();
					att.fromLook(look(yaw, pitch));
					att.rollLocal(bank);
					Vec3d f = att.forward();
					Vec3d u = att.up();

					Quaternionf q = ModelOrientation.basisRotation(yaw, f, u);
					if (q == null) continue;
					worstUp = Math.max(worstUp,
							angleBetween(throughStack(q, yaw, new Vector3f(0, 1, 0)), u));
					worstNose = Math.max(worstNose,
							angleBetween(throughStack(q, yaw, new Vector3f(0, 0, -1)), f));
				}
			}
		}
		assertTrue(worstUp < 0.5, "model up is " + worstUp + " degrees off");
		assertTrue(worstNose < 0.5, "model nose is " + worstNose + " degrees off");
	}

	@Test
	@DisplayName("level flight leaves the model exactly as vanilla drew it")
	void levelFlightIsIdentity() {
		for (int yaw = -180; yaw <= 180; yaw += 15) {
			Vec3d f = look(yaw, 0);
			Quaternionf q = ModelOrientation.basisRotation(yaw, f, ShipAttitude.levelUpOf(f));
			if (q == null) continue;
			assertEquals(0.0, q.x, 1e-4, "yaw=" + yaw);
			assertEquals(0.0, q.y, 1e-4, "yaw=" + yaw);
			assertEquals(0.0, q.z, 1e-4, "yaw=" + yaw);
			assertEquals(1.0, Math.abs(q.w), 1e-4, "yaw=" + yaw);
		}
	}

	@Test
	@DisplayName("degenerate input is refused rather than guessed at")
	void rejectsDegenerateBasis() {
		assertNull(ModelOrientation.basisRotation(0, null, new Vec3d(0, 1, 0)));
		assertNull(ModelOrientation.basisRotation(0, new Vec3d(0, 0, 1), null));
		assertNull(ModelOrientation.basisRotation(0, Vec3d.ZERO, new Vec3d(0, 1, 0)));
		// Up parallel to forward carries no orientation.
		assertNull(ModelOrientation.basisRotation(0, new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)));
	}
}
