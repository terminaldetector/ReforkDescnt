package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlAARotation;
import com.terminaldetector.drmd.world.cubic.CubicBlockCodec;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Experimental inert block assembly. All positions are COM positions; geometry is local cubic data. */
public final class BlockBodyEntity extends Entity {
    public record Cell(BlockPos pos, BlockState state) {}
    private static final TrackedData<NbtCompound> SHAPE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> POSE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final Set<Block> SUPPORTED = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.IRON_BLOCK,
        Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.OBSIDIAN, Blocks.GLASS, Blocks.BRICKS);
    private D6Volume<BlockState> volume = new D6Volume<>(Blocks.AIR.getDefaultState());
    private List<Cell> cells = List.of();
    private Vec3 centre = new Vec3(0, 0, 0);
    private final D6PhysicsBody body = new D6PhysicsBody().withLimits(20, 2);
    private Quat previousRotation = Quat.IDENTITY;
    private double radius = .5;

    public BlockBodyEntity(EntityType<? extends BlockBodyEntity> type, World world) {
        super(type, world); setNoGravity(true);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SHAPE, new NbtCompound()); builder.add(POSE, new NbtCompound());
    }
    public List<Cell> cells() { return cells; }
    public Vec3 localCentre() { return centre; }
    public Quat rotation() { return body.rotation(); }
    public Quat previousRotation() { return previousRotation; }
    public double radius() { return radius; }
    public D6PhysicsBody physics() { return body; }

    private void loadShape(NbtCompound tag) {
        volume = CubicBlockCodec.readVolume(tag, 8);
        List<Cell> next = new ArrayList<>();
        D6MassProperties mass = new D6MassProperties();
        for (long key : volume.keys()) {
            D6ChunkPos p = D6ChunkPos.fromLong(key);
            D6Cube<BlockState> cube = volume.cube(p);
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = cube.get(x, y, z);
                if (state.isAir()) continue;
                BlockPos pos = new BlockPos(p.minBlockX() + x, p.minBlockY() + y, p.minBlockZ() + z);
                if (!SUPPORTED.contains(state.getBlock()) || next.size() >= 256
                    || pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
                    || pos.getX() >= 16 || pos.getY() >= 16 || pos.getZ() >= 16)
                    throw new IllegalArgumentException("Unsupported or oversized body shape");
                next.add(new Cell(pos, state));
                mass.addBlock(new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5), 1);
            }
        }
        cells = List.copyOf(next); centre = mass.centreOfMass(); body.withMassProperties(mass);
        radius = .5;
        for (Cell c : cells) radius = Math.max(radius,
            new Vec3(c.pos.getX() + .5, c.pos.getY() + .5, c.pos.getZ() + .5).minus(centre).length() + Math.sqrt(3) / 2);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (getWorld().isClient && body != null) {
            if (SHAPE.equals(data)) loadShape(dataTracker.get(SHAPE));
            if (POSE.equals(data)) { previousRotation = body.rotation(); readPose(dataTracker.get(POSE)); }
        }
    }
    private NbtCompound pose() {
        NbtCompound tag = new NbtCompound();
        Quat q = body.rotation(); tag.putDouble("qx", q.x()); tag.putDouble("qy", q.y());
        tag.putDouble("qz", q.z()); tag.putDouble("qw", q.w());
        vector(tag, "v", body.linearVelocity()); vector(tag, "l", body.angularMomentum());
        return tag;
    }
    private static void vector(NbtCompound n, String key, Vec3 v) {
        n.putDouble(key + "x", v.x()); n.putDouble(key + "y", v.y()); n.putDouble(key + "z", v.z());
    }
    private static Vec3 vector(NbtCompound n, String key) {
        Vec3 v = new Vec3(n.getDouble(key + "x"), n.getDouble(key + "y"), n.getDouble(key + "z"));
        if (!Double.isFinite(v.lengthSquared())) throw new IllegalArgumentException("non-finite body vector");
        return v;
    }
    private void readPose(NbtCompound n) {
        if (!n.contains("qw")) return;
        Quat q = new Quat(n.getDouble("qx"), n.getDouble("qy"), n.getDouble("qz"), n.getDouble("qw"));
        if (!Double.isFinite(q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w())) throw new IllegalArgumentException("non-finite rotation");
        body.withRotation(q).withLinearVelocity(vector(n, "v")).withAngularMomentum(vector(n, "l"));
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n) {
        loadShape(n.getCompound("shape")); readPose(n.getCompound("body"));
        body.withPosition(pure(getPos())); previousRotation = body.rotation();
        dataTracker.set(SHAPE, CubicBlockCodec.writeVolume(volume)); dataTracker.set(POSE, pose());
    }
    @Override protected void writeCustomDataToNbt(NbtCompound n) {
        n.put("shape", CubicBlockCodec.writeVolume(volume)); n.put("body", pose());
    }
    public static BlockBodyEntity assemble(ServerWorld world, BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        if (max.getX() - min.getX() >= 16 || max.getY() - min.getY() >= 16 || max.getZ() - min.getZ() >= 16)
            throw new IllegalArgumentException("Selection must fit 16 x 16 x 16");
        D6Volume<BlockState> shape = new D6Volume<>(Blocks.AIR.getDefaultState());
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.iterate(min, max)) {
            BlockPos p = cursor.toImmutable();
            if (!world.isChunkLoaded(p) || !world.getWorldBorder().contains(p) || world.isOutOfHeightLimit(p))
                throw new IllegalArgumentException("Selection must be inside loaded world bounds");
            BlockState state = world.getBlockState(p);
            if (state.isAir()) continue;
            if (!SUPPORTED.contains(state.getBlock()) || world.getBlockEntity(p) != null || original.size() >= 256)
                throw new IllegalArgumentException("Up to 256 inert blocks: stone, cobblestone, iron/gold/diamond, obsidian, glass, bricks");
            original.put(p, state);
            shape.set(p.getX()-min.getX(), p.getY()-min.getY(), p.getZ()-min.getZ(), state);
        }
        if (original.isEmpty()) throw new IllegalArgumentException("Empty selection");
        BlockBodyEntity entity = new BlockBodyEntity(ModEntities.BLOCK_BODY, world);
        entity.loadShape(CubicBlockCodec.writeVolume(shape));
        entity.setPosition(min.getX()+entity.centre.x(), min.getY()+entity.centre.y(), min.getZ()+entity.centre.z());
        entity.body.withPosition(pure(entity.getPos()));
        entity.dataTracker.set(SHAPE, CubicBlockCodec.writeVolume(shape));
        entity.dataTracker.set(POSE, entity.pose());
        if (!world.spawnEntity(entity)) throw new IllegalArgumentException("Could not spawn body");
        try {
            for (BlockPos p : original.keySet())
                if (!world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS))
                    throw new IllegalStateException("Assembly blocked at " + p);
        } catch (RuntimeException failure) {
            original.forEach((p, state) -> world.setBlockState(p, state, Block.NOTIFY_LISTENERS));
            entity.discard(); throw failure;
        }
        original.forEach((p, state) -> world.updateNeighbors(p, state.getBlock()));
        return entity;
    }
    /** Snap to the nearest of the donor's 24 proper cube rotations. No reflections. */
    public void disassemble() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        ImmPtlAARotation turn = Arrays.stream(ImmPtlAARotation.values()).max(Comparator.comparingDouble(r -> {
            Quat q = r.quaternion, b = body.rotation();
            return Math.abs(q.x()*b.x()+q.y()*b.y()+q.z()*b.z()+q.w()*b.w());
        })).orElseThrow();
        Vec3 pivot = pure(getPos()).minus(turn.quaternion.rotate(centre.minus(new Vec3(.5,.5,.5))));
        BlockPos origin = BlockPos.ofFloored(pivot.x(), pivot.y(), pivot.z());
        Map<BlockPos, BlockState> target = new LinkedHashMap<>();
        for (Cell cell : cells) {
            BlockPos p = origin.add(turn.transform(cell.pos));
            if (!world.isChunkLoaded(p) || world.isOutOfHeightLimit(p) || !world.getWorldBorder().contains(p)
                || !world.getBlockState(p).isAir()) throw new IllegalArgumentException("Landing obstructed at " + p);
            target.put(p, cell.state);
        }
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (var e : target.entrySet()) {
                if (!world.setBlockState(e.getKey(), e.getValue(), Block.NOTIFY_LISTENERS))
                    throw new IllegalStateException("Landing failed at " + e.getKey());
                placed.add(e.getKey());
            }
        } catch (RuntimeException failure) {
            for (BlockPos p : placed) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            throw failure;
        }
        discard();
        target.forEach((p, state) -> world.updateNeighbors(p, state.getBlock()));
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) { setBoundingBox(new Box(getPos(), getPos()).expand(radius)); return; }
        if (cells.isEmpty()) return;
        body.withPosition(pure(getPos())); previousRotation = body.rotation();
        // Substeps bound corner travel to about 0.15 blocks, including rotation. Inelastic contact.
        int steps = Math.max(1, Math.min(32, (int)Math.ceil((20 + 2*radius) * .05 / .15)));
        for (int step = 0; step < steps; step++) {
            Vec3 before = body.position(); Quat rotation = body.rotation();
            body.step(.05 / steps);
            if (blocked()) {
                body.withPosition(before).withRotation(rotation)
                    .withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));
                break;
            }
        }
        Vec3 p = body.position(); setPosition(p.x(), p.y(), p.z());
        Vec3 v = body.linearVelocity(); setVelocity(v.x()/20, v.y()/20, v.z()/20);
        setBoundingBox(new Box(getPos(), getPos()).expand(radius));
        dataTracker.set(POSE, pose()); velocityModified = true;
    }
    private boolean blocked() {
        for (Cell cell : cells) {
            Box box = box(cell);
            if (box.minY < getWorld().getBottomY() || box.maxY > getWorld().getTopY()) return true;
            for (int x = MathHelper.floor(box.minX)>>4; x <= MathHelper.floor(box.maxX)>>4; x++)
                for (int z = MathHelper.floor(box.minZ)>>4; z <= MathHelper.floor(box.maxZ)>>4; z++)
                    if (!getWorld().isChunkLoaded(x,z)) return true;
            if (!getWorld().getWorldBorder().contains(box)) return true;
            if (getWorld().getBlockCollisions(this, box.contract(1e-6)).iterator().hasNext()) return true;
        }
        return false;
    }
    private Box box(Cell cell) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for (int x=0;x<=1;x++) for (int y=0;y<=1;y++) for (int z=0;z<=1;z++) {
            Vec3 p=body.rotation().rotate(new Vec3(cell.pos.getX()+x,cell.pos.getY()+y,cell.pos.getZ()+z).minus(centre)).plus(body.position());
            minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
        }
        return new Box(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public boolean canCrossPortal(D6PortalTransform transform, Vec3 exit) {
        Vec3 previous = body.position(); Quat rotation = body.rotation();
        try {
            body.withPosition(exit).withRotation(transform.transformOrientation(rotation));
            return !blocked();
        } finally { body.withPosition(previous).withRotation(rotation); }
    }
    public void crossPortal(D6PortalTransform transform) {
        body.withPosition(pure(getPos())); transform.apply(body, null);
        Vec3 p=body.position(); setPosition(p.x(),p.y(),p.z()); dataTracker.set(POSE,pose());
    }
    @Override public boolean canHit() { return true; }
    private static Vec3 pure(Vec3d v) { return new Vec3(v.x,v.y,v.z); }
}
