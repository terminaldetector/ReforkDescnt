/*
 * Copyright the Immersive Portals authors (qouteall and contributors).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---
 *
 * MODIFIED by the DRMD 6DOF project. Derived from qouteall.q_misc_util.my_util.IntBox in
 * Immersive Portals 6.0.6 (Minecraft 1.21.1, the Apache-2.0-licensed `1.21` line). Changes made:
 *
 *   - Translated from Mojang mappings to Yarn: betweenClosedStream to BlockPos.iterate, Vec3 to
 *     Vec3d. BlockPos.subtract and the AABB conversions are written out component-wise instead,
 *     since neither mapping has a caller in this project to check them against.
 *   - A record rather than a class, so that equality and hashing come from the components.
 *   - The half of the donor with no caller here is not carried over: forSixSurfaces and
 *     getSurfaceLayer (nether-portal frame scanning), get12Edges, getEightVertices, isOnEdge,
 *     isOnVertex, getSubBoxInCenter, confineInnerBox, map, and the two stream forms.
 *   - Apache Commons Validate replaced with plain checks; the size-zero case throws with the box
 *     in the message rather than a bare "signed size cannot be zero".
 */
package com.terminaldetector.drmd.vendor.immptl;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * An axis-aligned box of whole blocks, <b>both ends inclusive</b>.
 *
 * <p><b>Inclusive is the whole point,</b> and it is what separates this from {@code Box}. A region
 * of blocks is a set of block positions, not a volume of space: the box from {@code (0,0,0)} to
 * {@code (0,0,0)} is one block, and {@link #size} reports {@code (1,1,1)}. Every off-by-one in
 * block-region code comes from mixing that up with the half-open convention {@code Box} uses, so the
 * two are kept as separate types and this one never quietly turns into the other.
 *
 * <p><b>What DRMD needs it for.</b> The portal gun places a single block today —
 * {@code PortalGunItem} raycasts, takes {@code hit.getSide()}, and drops one panel. A rectangular
 * portal needs a region instead, and the placement algorithm read during the source audit (see
 * {@code docs/source-audit/algorithm-map.md}) is built on exactly two things: an
 * {@link ImmPtlAARotation} to orient the rectangle, and this to say where it lands. The rotation
 * turns a size vector, which is why {@link #fromPosAndSignedSize} exists — a rotated
 * {@code (width, height, 1)} routinely comes back with negative components, and a box built from a
 * corner and a signed extent handles that without the caller having to sort its own corners.
 *
 * <p>The corners are normalised on construction, so {@link #low} is componentwise minimal and
 * {@link #high} componentwise maximal however the two were passed in. The constructor copies them
 * while it is at it, which matters more than it sounds: {@link #positions} hands out vanilla's
 * shared mutable cursor, so building a box out of positions read during an iteration would
 * otherwise capture a reference that moves.
 */
public record ImmPtlIntBox(BlockPos low, BlockPos high) {

	public ImmPtlIntBox {
		BlockPos a = low;
		BlockPos b = high;
		low = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
				Math.min(a.getZ(), b.getZ()));
		high = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
				Math.max(a.getZ(), b.getZ()));
	}

	/** The single block at {@code pos}. */
	public static ImmPtlIntBox of(BlockPos pos) {
		return new ImmPtlIntBox(pos, pos);
	}

	/**
	 * From a corner and a count of blocks along each axis.
	 *
	 * @param size a count, so it reaches to {@code base + size - 1}. All three components must be
	 *             positive; see {@link #fromPosAndSignedSize} for the form that accepts negatives.
	 */
	public static ImmPtlIntBox fromBasePointAndSize(BlockPos base, Vec3i size) {
		if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
			throw new IllegalArgumentException("size must be positive on every axis, was " + size);
		}
		return new ImmPtlIntBox(base, base.add(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
	}

	/**
	 * From a corner and an extent that may point either way along each axis.
	 *
	 * <p>This is the form a rotated size needs. {@link ImmPtlAARotation#transform} applied to
	 * {@code (width, height, 1)} gives an extent whose components carry the direction the rectangle
	 * now runs in, and a negative one means "that many blocks the other way from {@code base}", with
	 * {@code base} itself always inside the box. Zero is rejected rather than treated as one, because
	 * a zero component in a rotated size means the rotation or the size was wrong, and silently
	 * making it a one-block-thick box would hide that.
	 */
	public static ImmPtlIntBox fromPosAndSignedSize(BlockPos base, Vec3i signedSize) {
		return new ImmPtlIntBox(base, new BlockPos(
				farEnd(base.getX(), signedSize.getX(), signedSize),
				farEnd(base.getY(), signedSize.getY(), signedSize),
				farEnd(base.getZ(), signedSize.getZ(), signedSize)));
	}

	private static int farEnd(int base, int signedSize, Vec3i whole) {
		if (signedSize > 0) return base + signedSize - 1;
		if (signedSize < 0) return base + signedSize + 1;
		throw new IllegalArgumentException("signed size cannot be zero on any axis, was " + whole);
	}

	/** The number of blocks along each axis — one more than the span, since both ends are inside. */
	public BlockPos size() {
		return new BlockPos(high.getX() - low.getX() + 1, high.getY() - low.getY() + 1,
				high.getZ() - low.getZ() + 1);
	}

	/** How many blocks the box holds. {@code long}, because a careless box can exceed an int. */
	public long volume() {
		BlockPos size = size();
		return (long) size.getX() * size.getY() * size.getZ();
	}

	/**
	 * The geometric centre, in world coordinates.
	 *
	 * <p>Offset by half a block from the average of the two corners, because a block position names
	 * a corner of its cube and the box therefore ends half a block past {@link #high}. For a
	 * single-block box at the origin this is {@code (0.5, 0.5, 0.5)}, which is where a portal's plane
	 * wants to sit.
	 */
	public Vec3d centre() {
		return new Vec3d(
				(low.getX() + high.getX() + 1) / 2.0,
				(low.getY() + high.getY() + 1) / 2.0,
				(low.getZ() + high.getZ() + 1) / 2.0);
	}

	public ImmPtlIntBox moved(int dx, int dy, int dz) {
		return new ImmPtlIntBox(low.add(dx, dy, dz), high.add(dx, dy, dz));
	}

	public ImmPtlIntBox moved(Vec3i offset) {
		return moved(offset.getX(), offset.getY(), offset.getZ());
	}

	/** One step along {@code direction} — the wall box behind a portal area, for instance. */
	public ImmPtlIntBox moved(Direction direction) {
		return moved(direction, 1);
	}

	public ImmPtlIntBox moved(Direction direction, int distance) {
		return moved(direction.getOffsetX() * distance, direction.getOffsetY() * distance,
				direction.getOffsetZ() * distance);
	}

	/**
	 * Every position in the box.
	 *
	 * <p><b>The cursor is shared.</b> Vanilla's iteration hands back the same mutable position each
	 * step, so a reference kept past the loop body will have moved by the time it is read. Call
	 * {@code toImmutable()} on anything that outlives the iteration.
	 */
	public Iterable<BlockPos> positions() {
		return BlockPos.iterate(low, high);
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= low.getX() && pos.getX() <= high.getX()
				&& pos.getY() >= low.getY() && pos.getY() <= high.getY()
				&& pos.getZ() >= low.getZ() && pos.getZ() <= high.getZ();
	}

	public boolean contains(ImmPtlIntBox other) {
		return contains(other.low) && contains(other.high);
	}

	/** The overlap of two boxes, or null where they do not touch. */
	public static ImmPtlIntBox intersect(ImmPtlIntBox a, ImmPtlIntBox b) {
		int lx = Math.max(a.low.getX(), b.low.getX());
		int ly = Math.max(a.low.getY(), b.low.getY());
		int lz = Math.max(a.low.getZ(), b.low.getZ());
		int hx = Math.min(a.high.getX(), b.high.getX());
		int hy = Math.min(a.high.getY(), b.high.getY());
		int hz = Math.min(a.high.getZ(), b.high.getZ());
		if (lx > hx || ly > hy || lz > hz) return null;
		return new ImmPtlIntBox(new BlockPos(lx, ly, lz), new BlockPos(hx, hy, hz));
	}

	/** Whether two boxes share at least one block. */
	public static boolean overlap(ImmPtlIntBox a, ImmPtlIntBox b) {
		return intersect(a, b) != null;
	}

	/** The smallest box holding both. */
	public static ImmPtlIntBox containing(ImmPtlIntBox a, ImmPtlIntBox b) {
		return new ImmPtlIntBox(
				new BlockPos(Math.min(a.low.getX(), b.low.getX()), Math.min(a.low.getY(), b.low.getY()),
						Math.min(a.low.getZ(), b.low.getZ())),
				new BlockPos(Math.max(a.high.getX(), b.high.getX()), Math.max(a.high.getY(), b.high.getY()),
						Math.max(a.high.getZ(), b.high.getZ())));
	}

	/** The box grown to hold {@code pos} as well. */
	public ImmPtlIntBox stretchedTo(BlockPos pos) {
		return containing(this, of(pos));
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("lX", low.getX());
		nbt.putInt("lY", low.getY());
		nbt.putInt("lZ", low.getZ());
		nbt.putInt("hX", high.getX());
		nbt.putInt("hY", high.getY());
		nbt.putInt("hZ", high.getZ());
		return nbt;
	}

	public static ImmPtlIntBox fromNbt(NbtCompound nbt) {
		return new ImmPtlIntBox(
				new BlockPos(nbt.getInt("lX"), nbt.getInt("lY"), nbt.getInt("lZ")),
				new BlockPos(nbt.getInt("hX"), nbt.getInt("hY"), nbt.getInt("hZ")));
	}

	@Override
	public String toString() {
		return "(" + low.getX() + " " + low.getY() + " " + low.getZ() + ")-("
				+ high.getX() + " " + high.getY() + " " + high.getZ() + ")";
	}
}
