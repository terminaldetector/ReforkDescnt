package com.terminaldetector.drmd;

import com.terminaldetector.drmd.vendor.immptl.ImmPtlIntBox;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inclusive block box.
 *
 * <p>Most of these guard the one thing this type exists to get right: both ends are inside, so a
 * box from a position to itself is one block and not none.
 */
class ImmPtlIntBoxTest {

	private static void assertPos(int x, int y, int z, BlockPos actual, String what) {
		assertEquals(x, actual.getX(), what + " x");
		assertEquals(y, actual.getY(), what + " y");
		assertEquals(z, actual.getZ(), what + " z");
	}

	@Test
	@DisplayName("the corners are sorted on construction, per axis and not as a pair")
	void cornersAreNormalised() {
		ImmPtlIntBox box = new ImmPtlIntBox(new BlockPos(5, 1, 9), new BlockPos(0, 4, 2));

		// x and z came in high-first, y came in low-first: each axis is decided on its own.
		assertPos(0, 1, 2, box.low(), "low");
		assertPos(5, 4, 9, box.high(), "high");
	}

	@Test
	@DisplayName("a box from one position to itself is one block, not none")
	void singleBlockBox() {
		ImmPtlIntBox box = ImmPtlIntBox.of(new BlockPos(7, -3, 12));

		assertPos(1, 1, 1, box.size(), "size");
		assertEquals(1, box.volume(), "volume");
		assertTrue(box.contains(new BlockPos(7, -3, 12)), "contains its own block");
		assertFalse(box.contains(new BlockPos(8, -3, 12)), "and nothing next to it");
	}

	@Test
	@DisplayName("fromBasePointAndSize counts blocks, so it reaches to base + size - 1")
	void fromBasePointAndSize() {
		ImmPtlIntBox box = ImmPtlIntBox.fromBasePointAndSize(new BlockPos(10, 20, 30), new Vec3i(3, 4, 5));

		assertPos(10, 20, 30, box.low(), "low");
		assertPos(12, 23, 34, box.high(), "high");
		assertEquals(60, box.volume(), "volume");
	}

	@Test
	@DisplayName("fromBasePointAndSize rejects a size that is not positive")
	void fromBasePointAndSizeRejectsEmpty() {
		assertThrows(IllegalArgumentException.class,
				() -> ImmPtlIntBox.fromBasePointAndSize(BlockPos.ORIGIN, new Vec3i(3, 0, 5)));
	}

	@Test
	@DisplayName("a signed size runs the other way but keeps the base inside")
	void fromPosAndSignedSize() {
		// What a rotated (3, 4, 5) looks like once the rotation has flipped the vertical axis.
		ImmPtlIntBox box = ImmPtlIntBox.fromPosAndSignedSize(new BlockPos(10, 20, 30), new Vec3i(3, -4, 5));

		// y runs 20 down to 17, so the box is the same shape, on the other side of the base.
		assertPos(10, 17, 30, box.low(), "low");
		assertPos(12, 20, 34, box.high(), "high");
		assertPos(3, 4, 5, box.size(), "size is unsigned");
		assertTrue(box.contains(new BlockPos(10, 20, 30)), "the base is inside whichever way it ran");
	}

	@Test
	@DisplayName("a zero component in a signed size is an error, not a one-block axis")
	void signedSizeRejectsZero() {
		assertThrows(IllegalArgumentException.class,
				() -> ImmPtlIntBox.fromPosAndSignedSize(BlockPos.ORIGIN, new Vec3i(3, 0, 5)));
	}

	@Test
	@DisplayName("the centre is half a block past the high corner, since a position names a corner")
	void centreIsGeometric() {
		Vec3d one = ImmPtlIntBox.of(BlockPos.ORIGIN).centre();
		assertEquals(0.5, one.x, 1e-9, "single block x");
		assertEquals(0.5, one.y, 1e-9, "single block y");
		assertEquals(0.5, one.z, 1e-9, "single block z");

		// Three blocks along x span [0, 3), so the middle is at 1.5 and not at 1.
		Vec3d three = new ImmPtlIntBox(BlockPos.ORIGIN, new BlockPos(2, 0, 0)).centre();
		assertEquals(1.5, three.x, 1e-9, "three blocks x");
		assertEquals(0.5, three.y, 1e-9, "three blocks y");
	}

	@Test
	@DisplayName("moved by a direction is the wall box behind an area")
	void movedByDirection() {
		ImmPtlIntBox area = new ImmPtlIntBox(new BlockPos(1, 2, 3), new BlockPos(2, 4, 6));
		ImmPtlIntBox wall = area.moved(Direction.NORTH);

		// North is -Z, and only Z moves.
		assertPos(1, 2, 2, wall.low(), "low");
		assertPos(2, 4, 5, wall.high(), "high");
		assertEquals(area.volume(), wall.volume(), "a move does not change the size");
	}

	@Test
	@DisplayName("iteration yields every block once, and the shared cursor does not leak into a box")
	void iterationCoversTheBox() {
		ImmPtlIntBox box = new ImmPtlIntBox(new BlockPos(1, 2, 3), new BlockPos(2, 4, 6));

		Set<BlockPos> seen = new HashSet<>();
		ImmPtlIntBox rebuilt = null;
		for (BlockPos p : box.positions()) {
			seen.add(p.toImmutable());
			rebuilt = rebuilt == null ? ImmPtlIntBox.of(p) : rebuilt.stretchedTo(p);
		}

		assertEquals(24, box.volume(), "2 x 3 x 4");
		assertEquals(24, seen.size(), "every position distinct");
		// If the constructor kept the iterator's mutable position instead of copying it, this box
		// would have collapsed onto wherever the cursor finished.
		assertEquals(box, rebuilt, "folding the positions back together gives the box again");
	}

	@Test
	@DisplayName("intersect gives the overlap, or null when there is none")
	void intersection() {
		ImmPtlIntBox a = new ImmPtlIntBox(BlockPos.ORIGIN, new BlockPos(4, 4, 4));
		ImmPtlIntBox b = new ImmPtlIntBox(new BlockPos(3, 3, 3), new BlockPos(9, 9, 9));
		ImmPtlIntBox apart = new ImmPtlIntBox(new BlockPos(6, 0, 0), new BlockPos(8, 2, 2));

		ImmPtlIntBox shared = ImmPtlIntBox.intersect(a, b);
		assertPos(3, 3, 3, shared.low(), "overlap low");
		assertPos(4, 4, 4, shared.high(), "overlap high");
		assertTrue(ImmPtlIntBox.overlap(a, b), "they overlap");

		assertNull(ImmPtlIntBox.intersect(a, apart), "no overlap");
		assertFalse(ImmPtlIntBox.overlap(a, apart), "and overlap says so");
	}

	@Test
	@DisplayName("boxes that only touch on a face still share a block, because both ends are inside")
	void touchingBoxesOverlap() {
		ImmPtlIntBox a = ImmPtlIntBox.of(BlockPos.ORIGIN);
		ImmPtlIntBox b = new ImmPtlIntBox(BlockPos.ORIGIN, new BlockPos(0, 0, 3));

		// The trap this type exists to avoid: with a half-open box these would be disjoint.
		assertTrue(ImmPtlIntBox.overlap(a, b), "the shared block is in both");
		assertTrue(b.contains(a), "and the smaller sits inside the larger");
		assertFalse(a.contains(b), "not the other way round");
	}

	@Test
	@DisplayName("containing is the union bounding box")
	void containingBothBoxes() {
		ImmPtlIntBox a = new ImmPtlIntBox(BlockPos.ORIGIN, new BlockPos(4, 4, 4));
		ImmPtlIntBox apart = new ImmPtlIntBox(new BlockPos(6, 0, 0), new BlockPos(8, 2, 2));

		ImmPtlIntBox both = ImmPtlIntBox.containing(a, apart);
		assertPos(0, 0, 0, both.low(), "low");
		assertPos(8, 4, 4, both.high(), "high");
		assertEquals(225, both.volume(), "9 x 5 x 5");
		assertTrue(both.contains(a) && both.contains(apart), "holds both");
	}

	@Test
	@DisplayName("a box survives the round trip through NBT")
	void nbtRoundTrip() {
		ImmPtlIntBox box = new ImmPtlIntBox(new BlockPos(-40, 7, 1200), new BlockPos(-38, 9, 1204));
		NbtCompound nbt = box.toNbt();

		assertEquals(box, ImmPtlIntBox.fromNbt(nbt), "same box back");
	}
}
