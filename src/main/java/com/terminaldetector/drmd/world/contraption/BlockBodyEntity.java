package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlAARotation;
import com.terminaldetector.drmd.world.cubic.CubicBlockCodec;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.registry.tag.BlockTags;
import java.util.*;

/** Experimental inert block assembly. All positions are COM positions; geometry is local cubic data. */
public final class BlockBodyEntity extends Entity implements PhysicsTarget {
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
    private Vec3 longAxis = new Vec3(0, 1, 0);
    private boolean gravity;
    public void setPhysicsGravity(boolean value) {gravity=value;dataTracker.set(POSE,pose());}
    public boolean physicsGravity() {return gravity;}
    public static boolean supports(BlockState state) {return SUPPORTED.contains(state.getBlock()) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.PLANKS);}

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
                if (!supports(state) || next.size() >= 256
                    || pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
                    || pos.getX() >= 16 || pos.getY() >= 16 || pos.getZ() >= 16)
                    throw new IllegalArgumentException("Unsupported or oversized body shape");
                next.add(new Cell(pos, state));
                mass.addBlock(new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5), state.isIn(BlockTags.LEAVES)?.08:1);
            }
        }
        cells = List.copyOf(next); centre = mass.centreOfMass(); body.withMassProperties(mass);
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(Cell c:cells) {
            minX=Math.min(minX,c.pos.getX());minY=Math.min(minY,c.pos.getY());minZ=Math.min(minZ,c.pos.getZ());
            maxX=Math.max(maxX,c.pos.getX());maxY=Math.max(maxY,c.pos.getY());maxZ=Math.max(maxZ,c.pos.getZ());
        }
        int sx=cells.isEmpty()?1:maxX-minX+1,sy=cells.isEmpty()?1:maxY-minY+1,sz=cells.isEmpty()?1:maxZ-minZ+1;
        longAxis=sx>=sy&&sx>=sz?new Vec3(1,0,0):sy>=sz?new Vec3(0,1,0):new Vec3(0,0,1);
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
        tag.putBoolean("gravity",gravity);
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
        gravity=n.getBoolean("gravity");
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
            if (!supports(state) || world.getBlockEntity(p) != null || original.size() >= 256)
                throw new IllegalArgumentException("Up to 256 supported inert blocks, including logs, leaves and planks");
            original.put(p, state);
            shape.set(p.getX()-min.getX(), p.getY()-min.getY(), p.getZ()-min.getZ(), state);
        }
        if (original.isEmpty()) throw new IllegalArgumentException("Empty selection");
        return assembleSelection(world,original);
    }
    /** Exact sparse selection: neighbouring machines and terrain outside the selection stay untouched. */
    public static BlockBodyEntity assembleSelection(ServerWorld world,Map<BlockPos,BlockState> original) {
        if(original.isEmpty() || original.size()>256)throw new IllegalArgumentException("Select 1..256 inert blocks");
        int mx=original.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int my=original.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int mz=original.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        BlockPos min=new BlockPos(mx,my,mz);
        D6Volume<BlockState> shape=new D6Volume<>(Blocks.AIR.getDefaultState());
        for(var e:original.entrySet()) {
            var p=e.getKey();var state=e.getValue();var local=p.subtract(min);
            if(!world.isChunkLoaded(p) || world.isOutOfHeightLimit(p) || !world.getWorldBorder().contains(p)
                || !world.getBlockState(p).equals(state) || !supports(state) || world.getBlockEntity(p)!=null
                || local.getX()>15 || local.getY()>15 || local.getZ()>15)
                throw new IllegalArgumentException("Unsupported, changed, unloaded or oversized selection");
            shape.set(local.getX(),local.getY(),local.getZ(),state);
        }
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
            Vec3 gravityDown = gravityDirection();
            if(gravity)body.applyForce(gravityDown.scaled(9.81*body.mass()));
            body.step(.05 / steps);
            if (blocked() && !(gravity && gravityContacts(before,rotation,gravityDown.scaled(-1)))) {
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

    /** A falling assembly follows station gravity, but remains ordinary world-gravity debris outside it. */
    private Vec3 gravityDirection() {
        if (!gravity) return new Vec3(0,-1,0);
        GravityFields.Sample field = GravityFields.sample(getWorld(), getPos());
        if (field == null || field.strength() < .05f || field.downDir().lengthSquared() < 1e-10)
            return new Vec3(0,-1,0);
        Vec3d down = field.downDir().normalize();
        return new Vec3(down.x,down.y,down.z);
    }

    private List<Vec3> corners(Cell cell,Vec3 position,Quat rotation) {
        var result=new ArrayList<Vec3>(8);
        for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++)
            result.add(rotation.rotate(new Vec3(cell.pos.getX()+x,cell.pos.getY()+y,cell.pos.getZ()+z).minus(centre)).plus(position));
        return result;
    }
    /**
     * The local-gravity floor may be a world floor, wall or ceiling. Resolve against the cardinal
     * face most aligned with local UP, transfer point impulses there and keep tangential motion.
     */
    private boolean gravityContacts(Vec3 before,Quat previous,Vec3 up) {
        if (up.lengthSquared()<1e-10) return false;
        up=up.scaled(1/up.length());
        int axis=dominantAxis(up);double sign=component(up,axis)>=0?1:-1;
        Vec3 normal=axisVector(axis,sign);
        List<Vec3> contacts=new ArrayList<>();double lift=0;
        for(Cell cell:cells) {
            Box bounds=box(cell);
            List<Vec3> oldCorners=corners(cell,before,previous);
            double previousNear=sign>0
                ? oldCorners.stream().mapToDouble(p->component(p,axis)).min().orElseThrow()
                : oldCorners.stream().mapToDouble(p->component(p,axis)).max().orElseThrow();
            List<Vec3> currentCorners=corners(cell,body.position(),body.rotation());
            for(var shape:getWorld().getBlockCollisions(this,bounds.contract(1e-6))) {
                Box obstacle=shape.getBoundingBox();
                double surface=sign>0?boxMax(obstacle,axis):boxMin(obstacle,axis);
                double currentNear=sign>0?boxMin(bounds,axis):boxMax(bounds,axis);
                double penetration=sign>0?surface-currentNear:currentNear-surface;
                // A side impact or a body already deep inside terrain is not a gravity support.
                if((sign>0 && previousNear<surface-.06)||(sign<0 && previousNear>surface+.06)
                    || penetration<0 || penetration>.2)return false;
                lift=Math.max(lift,penetration);
                for(Vec3 corner:currentCorners) {
                    double near=component(corner,axis);
                    if((sign>0?near<=surface+.001:near>=surface-.001) && insideTangents(corner,obstacle,axis))
                        contacts.add(withComponent(corner,axis,surface));
                }
            }
        }
        if(contacts.isEmpty() || lift>.2)return false;
        body.withPosition(body.position().plus(normal.scaled(Math.max(0,lift)+1e-6)));
        for(int pass=0;pass<4;pass++)for(Vec3 point:contacts)
            body.contactImpulse(point.minus(body.position()),normal,.65);
        return !blocked();
    }
    private static int dominantAxis(Vec3 v) {
        double ax=Math.abs(v.x()),ay=Math.abs(v.y()),az=Math.abs(v.z());
        return ax>=ay&&ax>=az?0:ay>=az?1:2;
    }
    private static Vec3 axisVector(int axis,double value) {
        return axis==0?new Vec3(value,0,0):axis==1?new Vec3(0,value,0):new Vec3(0,0,value);
    }
    private static double component(Vec3 v,int axis) {return axis==0?v.x():axis==1?v.y():v.z();}
    private static Vec3 withComponent(Vec3 v,int axis,double value) {
        return axis==0?new Vec3(value,v.y(),v.z()):axis==1?new Vec3(v.x(),value,v.z()):new Vec3(v.x(),v.y(),value);
    }
    private static double boxMin(Box b,int axis) {return axis==0?b.minX:axis==1?b.minY:b.minZ;}
    private static double boxMax(Box b,int axis) {return axis==0?b.maxX:axis==1?b.maxY:b.maxZ;}
    private static boolean insideTangents(Vec3 p,Box b,int axis) {
        return (axis==0||(p.x()>=b.minX-1e-6&&p.x()<=b.maxX+1e-6))
            && (axis==1||(p.y()>=b.minY-1e-6&&p.y()<=b.maxY+1e-6))
            && (axis==2||(p.z()>=b.minZ-1e-6&&p.z()<=b.maxZ+1e-6));
    }
    /** Ray tests local occupied cubes, so hollow space in a body's broadphase remains passable. */
    public Optional<Vec3d> raycast(Vec3d start,Vec3d end) {
        Vec3 a=body.rotation().inverse().rotate(pure(start).minus(pure(getPos()))).plus(centre);
        Vec3 b=body.rotation().inverse().rotate(pure(end).minus(pure(getPos()))).plus(centre);
        Vec3d from=new Vec3d(a.x(),a.y(),a.z()),to=new Vec3d(b.x(),b.y(),b.z());
        Vec3d best=null;double distance=Double.POSITIVE_INFINITY;
        for(Cell cell:cells) {
            var hit=new Box(cell.pos).raycast(from,to);
            if(hit.isPresent() && from.squaredDistanceTo(hit.get())<distance) {best=hit.get();distance=from.squaredDistanceTo(best);}
        }
        if(best==null)return Optional.empty();
        Vec3 world=body.rotation().rotate(pure(best).minus(centre)).plus(pure(getPos()));
        return Optional.of(new Vec3d(world.x(),world.y(),world.z()));
    }
    @Override public Optional<Vec3d> physicsRaycast(Vec3d start,Vec3d end) {return raycast(start,end);}
    @Override public void applyPhysicsImpulse(Vec3d impact,Vec3d impulse) {
        if(getWorld().isClient || !Double.isFinite(impact.lengthSquared()) || !Double.isFinite(impulse.lengthSquared()))return;
        body.applyImpulse(pure(impulse),pure(impact).minus(pure(getPos())));
        dataTracker.set(POSE,pose());velocityModified=true;
    }
    @Override public Vec3d physicsVelocity() {
        Vec3 velocity=body.linearVelocity();return new Vec3d(velocity.x(),velocity.y(),velocity.z());
    }
    @Override public double physicsMass() {return body.mass();}
    @Override public Optional<Vec3d> physicsLongAxis() {
        Vec3 axis=body.rotation().rotate(longAxis);
        return Optional.of(new Vec3d(axis.x(),axis.y(),axis.z()));
    }
    @Override public Vec3d physicsAngularVelocity() {
        Vec3 velocity=body.angularVelocity();return new Vec3d(velocity.x(),velocity.y(),velocity.z());
    }
    @Override public void applyPhysicsTorque(Vec3d torque) {
        if(getWorld().isClient || !Double.isFinite(torque.lengthSquared()))return;
        body.applyTorque(pure(torque));dataTracker.set(POSE,pose());velocityModified=true;
    }
    @Override public void enablePhysicsGravity() {setPhysicsGravity(true);}
    public void weaponImpulse(Vec3d impact,Vec3d direction,float damage) {
        if(getWorld().isClient || !Float.isFinite(damage) || damage<=0 || !Double.isFinite(direction.lengthSquared()))return;
        applyPhysicsImpulse(impact,direction.normalize().multiply(Math.min(80,damage*.15)));
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
