package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.PhysicsTarget;
import com.terminaldetector.drmd.physics.collision.*;
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

/** Inert local block grid. Physics position is COM; Entity tracks a fixed grid pivot across mass edits. */
public final class BlockBodyEntity extends Entity implements PhysicsTarget {
    public record Cell(BlockPos pos, BlockState state) {}
    private static java.util.function.BiConsumer<BlockBodyEntity,Vec3> clientShapeListener=(b,p)->{};
    public static void clientShapeListener(java.util.function.BiConsumer<BlockBodyEntity,Vec3> listener){clientShapeListener=java.util.Objects.requireNonNull(listener);}
    public static final int MIN_LOCAL=-8,MAX_LOCAL=24;
    public static boolean validLocal(BlockPos p){return p.getX()>=MIN_LOCAL&&p.getY()>=MIN_LOCAL&&p.getZ()>=MIN_LOCAL&&p.getX()<MAX_LOCAL&&p.getY()<MAX_LOCAL&&p.getZ()<MAX_LOCAL;}
    private static final TrackedData<NbtCompound> SHAPE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> POSE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final Set<Block> SUPPORTED = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.IRON_BLOCK,
        Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.OBSIDIAN, Blocks.GLASS, Blocks.BRICKS);
    private static final double MAX_LINEAR_SPEED = 50;
    private static final double MAX_ANGULAR_SPEED = 2;
    private D6Volume<BlockState> volume = new D6Volume<>(Blocks.AIR.getDefaultState());
    private List<Cell> cells = List.of();
    private Map<BlockPos,com.terminaldetector.drmd.world.geometry.HybridMesh> surfaces=Map.of();
    public com.terminaldetector.drmd.world.geometry.HybridMesh surface(Cell cell) {return surfaces.get(cell.pos());}
    private Map<BlockPos,BodyBounds> cellBounds=Map.of();
    private CompoundCollider collider=new CompoundCollider(List.of());
    public CompoundCollider collider(){return collider;}
    private Vec3 centre = new Vec3(0, 0, 0);
    private Vec3 pivot = new Vec3(0,0,0);
    private long shapeRevision;
    private D6MassProperties massProperties=new D6MassProperties();
    private final D6PhysicsBody body = new D6PhysicsBody().withLimits(MAX_LINEAR_SPEED, MAX_ANGULAR_SPEED);
    private Quat previousRotation = Quat.IDENTITY;
    private Vec3 previousPhysicsPosition = new Vec3(0,0,0);
    private long physicsTick = Long.MIN_VALUE;
    private boolean remoteMotionPaused=true;
    /** Non-ticking entities retain their momentum for resume, but their current contact surface is still. */
    public boolean boardingMotionPaused(){return getWorld().isClient?remoteMotionPaused:physicsTick!=getWorld().getTime();}
    private final BodyRotationTimeline displayRotation = new BodyRotationTimeline();
    private double radius = .5;
    private Vec3 longAxis = new Vec3(0, 1, 0);
    private Vec3 localAim=new Vec3(0,0,0);
    private Vec3 pendingTorque = new Vec3(0, 0, 0);
    private boolean gravity;
    private Vec3 passengerGravity=new Vec3(0,0,0);
    public Vec3 passengerGravity(){return passengerGravity;}
    public com.terminaldetector.drmd.world.contraption.boarding.BodyGravityField passengerField(Vec3 acceleration) {
        var bounds=collider.bounds().orElse(new BodyBounds(new Vec3(-.5,-.5,-.5),new Vec3(.5,.5,.5)));
        var pad=new Vec3(4,4,4);
        return new com.terminaldetector.drmd.world.contraption.boarding.BodyGravityField(acceleration,new BodyBounds(bounds.min().minus(pad),bounds.max().plus(pad)));
    }
    public void setPassengerGravity(Vec3 acceleration) {
        if(getWorld().isClient)throw new IllegalStateException("Ship gravity is server-owned");
        passengerField(acceleration);passengerGravity=acceleration;dataTracker.set(POSE,pose());
    }
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
    public Vec3 localPivot(){return pivot;}
    public long shapeRevision(){return shapeRevision;}
    public Vec3 massPosition(){return pure(getPos()).plus(body.rotation().rotate(centre.minus(pivot)));}
    public Vec3 renderMassPosition(float delta){return pure(getLerpedPos(delta)).plus(renderRotation(delta).rotate(centre.minus(pivot)));}
    private void positionFromPhysics(){
        Vec3 at=body.position().plus(body.rotation().rotate(pivot.minus(centre)));setPosition(at.x(),at.y(),at.z());
    }
    private NbtCompound shapeTag(){
        var n=CubicBlockCodec.writeVolume(volume);vector(n,"pivot",pivot);n.putLong("revision",shapeRevision);return n;
    }
    public Quat rotation() { return body.rotation(); }
    public Quat previousRotation() { return previousRotation; }
    public Quat renderRotation(float delta) {
        return getWorld().isClient ? displayRotation.sample(age+MathHelper.clamp(delta,0,1)) : body.rotation();
    }
    public double radius() { return radius; }
    public D6PhysicsBody physics() { return body; }
    public com.terminaldetector.drmd.world.contraption.boarding.BodyMotion boardingMotion() {
        Vec3 end=massPosition();
        boolean fresh=!boardingMotionPaused();
        Vec3 start=fresh?previousPhysicsPosition:end;
        Quat before=fresh?previousRotation:body.rotation();
        D6PhysicsBody dynamics=getWorld().isClient?new D6PhysicsBody().withMass(body.mass()).withInertia(body.inertia())
            .withPosition(end).withRotation(body.rotation()).withLinearVelocity(body.linearVelocity()).withAngularMomentum(body.angularMomentum()):body;
        return new com.terminaldetector.drmd.world.contraption.boarding.BodyMotion(getId(),collider,centre,
            start,before,end,body.rotation(),dynamics,passengerField(passengerGravity),!fresh);
    }
    public void publishPhysicsState() {
        if(!getWorld().isClient){dataTracker.set(POSE,pose());velocityModified=true;}
    }
    /** Current world pose plus the existing local BVH; no world blocks or chunk loads are read. */
    public BodyCollisionQuery collisionQuery() {
        return new BodyCollisionQuery(collider,massPosition(),centre,body.rotation());
    }

    private void loadShape(NbtCompound tag) {
        Vec3 oldPivot=pivot;var previous=new HashMap<BlockPos,BlockState>();for(var c:cells)previous.put(c.pos(),c.state());
        volume = CubicBlockCodec.readVolume(tag, 27);
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
                    || !validLocal(pos))
                    throw new IllegalArgumentException("Unsupported or oversized body shape");
                next.add(new Cell(pos, state));
                mass.addBlock(new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5), blockMass(state));
            }
        }
        cells = List.copyOf(next); massProperties=mass;centre = mass.centreOfMass(); body.withMassProperties(mass);
        pivot=tag.contains("pivotx")?vector(tag,"pivot"):centre;shapeRevision=tag.getLong("revision");
        Set<BlockPos> changed=null;
        if(!previous.isEmpty()&&oldPivot.equals(pivot)) {
            changed=new HashSet<>();var remaining=new HashMap<>(previous);
            for(var cell:cells)if(!cell.state().equals(remaining.remove(cell.pos())))markGeometryNeighbours(changed,cell.pos());
            for(var pos:remaining.keySet())markGeometryNeighbours(changed,pos);
        }
        rebuildGeometry(changed);
    }
    private static void markGeometryNeighbours(Set<BlockPos> dirty,BlockPos pos) {
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)dirty.add(pos.add(x,y,z));
    }
    /** Preserve unaffected surface meshes; rebuild only changed blocks and their 26 neighbours. */
    private void rebuildGeometry(Set<BlockPos> dirty) {
        var meshes=new HashMap<BlockPos,com.terminaldetector.drmd.world.geometry.HybridMesh>(surfaces);
        var occupied=new HashSet<BlockPos>();for(var cell:cells)occupied.add(cell.pos());
        meshes.keySet().removeIf(p->!occupied.contains(p));
        for(var cell:cells) {
            if(dirty!=null&&!dirty.contains(cell.pos()))continue;
            if(!com.terminaldetector.drmd.world.geometry.NaturalGeometry.applies(cell.state())){meshes.remove(cell.pos());continue;}
            var neighbours=new ArrayList<com.terminaldetector.drmd.world.geometry.HybridMesh.Block>(27);
            for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++) {
                var state=volume.get(cell.pos().getX()+x,cell.pos().getY()+y,cell.pos().getZ()+z);
                boolean natural=com.terminaldetector.drmd.world.geometry.NaturalGeometry.applies(state);
                neighbours.add(state.isAir()?com.terminaldetector.drmd.world.geometry.HybridMesh.Block.AIR:
                    new com.terminaldetector.drmd.world.geometry.HybridMesh.Block(-1L,natural,!natural,
                        state.isOpaque() || state.equals(cell.state())));
            }
            meshes.put(cell.pos(),new com.terminaldetector.drmd.world.geometry.HybridMesh(
                new com.terminaldetector.drmd.world.geometry.HybridMesh.Neighbourhood(neighbours)));
        }
        surfaces=Map.copyOf(meshes);
        var solid=new ArrayList<CompoundCollider.Cell>();var shaped=new ArrayList<BodyBounds>();
        var perCell=new HashMap<BlockPos,BodyBounds>(cellBounds);
        perCell.keySet().removeIf(p->!occupied.contains(p));
        for(Cell cell:cells) {
            if(surface(cell)==null) {
                solid.add(new CompoundCollider.Cell(cell.pos().getX(),cell.pos().getY(),cell.pos().getZ()));
                perCell.put(cell.pos(),new BodyBounds(new Vec3(cell.pos().getX(),cell.pos().getY(),cell.pos().getZ()),
                    new Vec3(cell.pos().getX()+1,cell.pos().getY()+1,cell.pos().getZ()+1)));
            }
            else if(dirty==null||dirty.contains(cell.pos())) {
                double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=-x0,z1=-x0;
                for(Vec3 v:surfaceCorners(cell,new Vec3(0,0,0),Quat.IDENTITY)) {
                    v=v.plus(centre);x0=Math.min(x0,v.x());y0=Math.min(y0,v.y());z0=Math.min(z0,v.z());
                    x1=Math.max(x1,v.x());y1=Math.max(y1,v.y());z1=Math.max(z1,v.z());
                }
                var bounds=new BodyBounds(new Vec3(x0,y0,z0),new Vec3(x1,y1,z1));
                perCell.put(cell.pos(),bounds);
            }
            if(surface(cell)!=null)shaped.add(perCell.get(cell.pos()));
        }
        cellBounds=Map.copyOf(perCell);
        localAim=cellBounds.values().stream().min(Comparator.comparingDouble(b->b.centre().minus(centre).lengthSquared())).map(BodyBounds::centre).orElse(centre);
        shaped.addAll(CompoundCollider.cells(solid).boxes());collider=new CompoundCollider(shaped);

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
            Math.max(new Vec3(c.pos.getX()+.5,c.pos.getY()+.5,c.pos.getZ()+.5).minus(pivot).length(),new Vec3(c.pos.getX()+.5,c.pos.getY()+.5,c.pos.getZ()+.5).minus(centre).length()) + Math.sqrt(3) / 2);
    }
    public BlockState getLocalBlock(BlockPos pos){return !validLocal(pos)?Blocks.AIR.getDefaultState():volume.get(pos.getX(),pos.getY(),pos.getZ());}
    public Map<BlockPos,BodyBounds> localBlockBounds(){return cellBounds;}
    /** Server-only local-grid mutation. COM rebasing preserves every remaining material point. */
    public boolean setLocalBlock(BlockPos pos,BlockState state) {
        if(!(getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())throw new IllegalStateException("Local edits are server-thread owned");
        if(isRemoved()||!validLocal(pos)
            ||(!state.isAir()&&!supports(state)))return false;
        BlockState before=getLocalBlock(pos);if(before.equals(state))return false;
        if(before.isAir()&&!state.isAir()&&cells.size()>=256)return false;
        Vec3 origin=massPosition();Vec3 spin=body.angularVelocity(),velocity=body.linearVelocity();
        Vec3 oldCentre=centre,blockCentre=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        if(!before.isAir())massProperties.addBlock(blockCentre,-blockMass(before));
        if(!state.isAir())massProperties.addBlock(blockCentre,blockMass(state));
        volume.set(pos.getX(),pos.getY(),pos.getZ(),state);
        var next=new ArrayList<Cell>();for(var c:cells)if(!c.pos().equals(pos))next.add(c);
        if(!state.isAir())next.add(new Cell(pos.toImmutable(),state));
        next.sort(Comparator.comparingInt((Cell c)->c.pos().getX()).thenComparingInt(c->c.pos().getY()).thenComparingInt(c->c.pos().getZ()));
        cells=List.copyOf(next);centre=massProperties.centreOfMass();
        Vec3 shift=body.rotation().rotate(centre.minus(oldCentre));
        body.withMassProperties(massProperties).withPosition(origin.plus(shift))
            .withLinearVelocity(velocity.plus(spin.cross(shift))).withAngularVelocity(spin);
        previousPhysicsPosition=previousPhysicsPosition.plus(previousRotation.rotate(centre.minus(oldCentre)));
        var dirty=new HashSet<BlockPos>();markGeometryNeighbours(dirty,pos);
        rebuildGeometry(dirty);shapeRevision++;
        setBoundingBox(new Box(getPos(),getPos()).expand(radius));
        dataTracker.set(SHAPE,shapeTag());dataTracker.set(POSE,pose());velocityModified=true;
        if(cells.isEmpty())discard();return true;
    }
    private static double blockMass(BlockState state){return state.isIn(BlockTags.LEAVES)?.08:1;}
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (getWorld().isClient && body != null) {
            if (SHAPE.equals(data)){Vec3 before=centre;loadShape(dataTracker.get(SHAPE));body.withPosition(massPosition());setBoundingBox(new Box(getPos(),getPos()).expand(radius));clientShapeListener.accept(this,before);}
            if (POSE.equals(data)) {
                previousRotation = body.rotation(); readPose(dataTracker.get(POSE));
                displayRotation.receive(body.rotation(),age);
            }
        }
    }
    private NbtCompound pose() {
        NbtCompound tag = new NbtCompound();
        Quat q = body.rotation(); tag.putDouble("qx", q.x()); tag.putDouble("qy", q.y());
        tag.putDouble("qz", q.z()); tag.putDouble("qw", q.w());
        tag.putBoolean("gravity",gravity);
        tag.putBoolean("motionPaused",boardingMotionPaused());
        vector(tag,"passengerGravity",passengerGravity);
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
        remoteMotionPaused=n.getBoolean("motionPaused");
        passengerGravity=vector(n,"passengerGravity");passengerField(passengerGravity);
        Quat q = new Quat(n.getDouble("qx"), n.getDouble("qy"), n.getDouble("qz"), n.getDouble("qw"));
        if (!Double.isFinite(q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w())) throw new IllegalArgumentException("non-finite rotation");
        body.withRotation(q).withLinearVelocity(vector(n, "v")).withAngularMomentum(vector(n, "l")).withPosition(massPosition());
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n) {
        loadShape(n.getCompound("shape")); readPose(n.getCompound("body"));
        body.withPosition(massPosition()); previousRotation = body.rotation();
        displayRotation.reset(body.rotation(),age);
        dataTracker.set(SHAPE, shapeTag()); dataTracker.set(POSE, pose());
    }
    @Override protected void writeCustomDataToNbt(NbtCompound n) {
        n.put("shape", shapeTag()); n.put("body", pose());
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
            var snapshot=com.terminaldetector.drmd.world.access.WorldAccess.of(world).snapshotIfLoaded(p);
            if(snapshot.isEmpty() || !world.getWorldBorder().contains(p) || world.isOutOfHeightLimit(p))
                throw new IllegalArgumentException("Selection must be inside loaded world bounds");
            BlockState state=snapshot.get().state();
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
            var snapshot=com.terminaldetector.drmd.world.access.WorldAccess.of(world).snapshotIfLoaded(p);
            if(snapshot.isEmpty() || world.isOutOfHeightLimit(p) || !world.getWorldBorder().contains(p)
                || !snapshot.get().state().equals(state) || !supports(state) || snapshot.get().entityData()!=null
                || local.getX()>15 || local.getY()>15 || local.getZ()>15)
                throw new IllegalArgumentException("Unsupported, changed, unloaded or oversized selection");
            shape.set(local.getX(),local.getY(),local.getZ(),state);
        }
        BlockBodyEntity entity = new BlockBodyEntity(ModEntities.BLOCK_BODY, world);
        entity.loadShape(CubicBlockCodec.writeVolume(shape));
        entity.setPosition(min.getX()+entity.centre.x(), min.getY()+entity.centre.y(), min.getZ()+entity.centre.z());
        entity.body.withPosition(pure(entity.getPos()));
        entity.dataTracker.set(SHAPE, entity.shapeTag());
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
        original.forEach((p, state) -> {
            world.updateNeighbors(p, state.getBlock());
            com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world,p);
        });
        return entity;
    }
    /** Snap to the nearest of the donor's 24 proper cube rotations. No reflections. */
    public void disassemble() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        ImmPtlAARotation turn = Arrays.stream(ImmPtlAARotation.values()).max(Comparator.comparingDouble(r -> {
            Quat q = r.quaternion, b = body.rotation();
            return Math.abs(q.x()*b.x()+q.y()*b.y()+q.z()*b.z()+q.w()*b.w());
        })).orElseThrow();
        Vec3 pivot = massPosition().minus(turn.quaternion.rotate(centre.minus(new Vec3(.5,.5,.5))));
        BlockPos origin = BlockPos.ofFloored(pivot.x(), pivot.y(), pivot.z());
        Map<BlockPos, BlockState> target = new LinkedHashMap<>();
        for (Cell cell : cells) {
            BlockPos p = origin.add(turn.transform(cell.pos));
            var state=com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(p);
            if(state.isEmpty() || world.isOutOfHeightLimit(p) || !world.getWorldBorder().contains(p)
                || !state.get().isAir())throw new IllegalArgumentException("Landing obstructed at "+p);
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
        target.forEach((p, state) -> {
            world.updateNeighbors(p, state.getBlock());
            com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world,p);
        });
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) { setBoundingBox(new Box(getPos(), getPos()).expand(radius)); return; }
        if (cells.isEmpty()) return;
        body.withPosition(massPosition()); previousRotation = body.rotation();
        previousPhysicsPosition=body.position();physicsTick=getWorld().getTime();
        // Substeps bound corner travel to about 0.15 blocks, including rotation. Inelastic contact.
        int steps = Math.max(1, Math.min(32,
            (int)Math.ceil((MAX_LINEAR_SPEED + MAX_ANGULAR_SPEED*radius) * .05 / .15)));
        Vec3 externalTorque=pendingTorque;pendingTorque=new Vec3(0,0,0);
        for (int step = 0; step < steps; step++) {
            Vec3 before = body.position(); Quat rotation = body.rotation();
            Vec3 gravityDown = gravityDirection();
            if(gravity)body.applyForce(gravityDown.scaled(9.81*body.mass()));
            // D6PhysicsBody clears force/torque after every integration step. Reapply the frame's
            // external torque to each collision substep so its total impulse remains torque*0.05,
            // independent of body speed/radius and therefore of the chosen substep count.
            body.applyTorque(externalTorque);
            body.step(.05 / steps);
            if (blocked() && !(gravity && gravityContacts(before,rotation,gravityDown.scaled(-1)))) {
                body.withPosition(before).withRotation(rotation)
                    .withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));
                break;
            }
        }
        positionFromPhysics();
        Vec3 v = pure(surfaceVelocityAt(getPos())); setVelocity(v.x()/20, v.y()/20, v.z()/20);
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

    private List<Vec3> surfaceCorners(Cell cell,Vec3 position,Quat rotation) {
        var result=new ArrayList<Vec3>(8);
        var mesh=surface(cell);
        for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++) {
            var p=mesh==null?new com.terminaldetector.drmd.world.geometry.HybridMesh.Point(x,y,z):mesh.vertex(x*4,y*4,z*4);
            result.add(rotation.rotate(new Vec3(cell.pos.getX()+p.x(),cell.pos.getY()+p.y(),cell.pos.getZ()+p.z()).minus(centre)).plus(position));
        }
        return result;
    }
    /** Support contacts and SAT must use the same conservative physical primitive. */
    private List<Vec3> corners(Cell cell,Vec3 position,Quat rotation) {
        BodyBounds bounds=cellBounds.get(cell.pos());var result=new ArrayList<Vec3>(8);
        for(double x:new double[]{bounds.min().x(),bounds.max().x()})
            for(double y:new double[]{bounds.min().y(),bounds.max().y()})
                for(double z:new double[]{bounds.min().z(),bounds.max().z()})
                    result.add(rotation.rotate(new Vec3(x,y,z).minus(centre)).plus(position));
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
            if(!collisionRegionAvailable(bounds))return false;
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
        // The same two-sided constraint will serve moving supports. Terrain is one immovable
        // participant, never one RigidBody per block; keep accumulated impulses within this step.
        var terrain=new D6PhysicsBody();
        var constraints=contacts.stream().map(point->new BodyContactConstraint(body,terrain,point,normal,.65)).toList();
        for(int pass=0;pass<4;pass++)for(var contact:constraints)contact.solve();
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
    public Vec3d worldToLocal(Vec3d point) {
        return minecraft(body.rotation().inverse().rotate(pure(point).minus(massPosition())).plus(centre));
    }
    public Vec3d localToWorld(Vec3d point) {
        return minecraft(body.rotation().rotate(pure(point).minus(centre)).plus(massPosition()));
    }
    /** World units per second, including angular velocity about the body's actual COM. */
    public Vec3d surfaceVelocityAt(Vec3d point) {return minecraft(body.velocityAtPoint(pure(point).minus(massPosition())));}
    /** BVH stays in local space; holes in the hull never become solid broadphase volume. */
    public Optional<CompoundCollider.RayHit> raycastLocal(Vec3d start,Vec3d end) {
        return collider.raycast(pure(worldToLocal(start)),pure(worldToLocal(end)));
    }
    public Optional<Vec3d> raycast(Vec3d start,Vec3d end) {
        return raycastLocal(start,end).map(hit->localToWorld(minecraft(hit.point())));
    }
    @Override public Optional<Vec3d> physicsRaycast(Vec3d start,Vec3d end) {return raycast(start,end);}
    @Override public void applyPhysicsImpulse(Vec3d impact,Vec3d impulse) {
        if(getWorld().isClient || !Double.isFinite(impact.lengthSquared()) || !Double.isFinite(impulse.lengthSquared()))return;
        body.applyImpulse(pure(impulse),pure(impact).minus(massPosition()));
        dataTracker.set(POSE,pose());velocityModified=true;
    }
    @Override public Vec3d physicsCentre(){return minecraft(massPosition());}
    @Override public Vec3d physicsAimPoint(){return localToWorld(minecraft(localAim));}
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
        pendingTorque=pendingTorque.plus(pure(torque));velocityModified=true;
    }
    @Override public void enablePhysicsGravity() {setPhysicsGravity(true);}
    public void weaponImpulse(Vec3d impact,Vec3d direction,float damage) {
        if(getWorld().isClient || !Float.isFinite(damage) || damage<=0 || !Double.isFinite(direction.lengthSquared()))return;
        applyPhysicsImpulse(impact,direction.normalize().multiply(Math.min(80,damage*.15)));
    }
    /** World shape boxes are candidates. Only an actual oriented overlap blocks the body. */
    private boolean blocked() {
        var localBounds=collider.bounds();if(localBounds.isEmpty())return false;
        Box broad=minecraft(OrientedBox.local(localBounds.get(),body.position(),centre,body.rotation()).bounds());
        if(!collisionRegionAvailable(broad))return true;
        Quat inverse=body.rotation().inverse();
        for(var shape:getWorld().getBlockCollisions(this,broad.contract(1e-6)))for(Box obstacle:shape.getBoundingBoxes()) {
            BodyBounds worldBox=bounds(obstacle);
            var localObstacle=new OrientedBox(inverse.rotate(worldBox.centre().minus(body.position())).plus(centre),worldBox.half(),inverse);
            for(BodyBounds solid:collider.query(localObstacle.bounds()))
                if(OrientedBox.axisAligned(solid).contact(localObstacle,0)
                    .filter(contact->contact.penetration()>1e-6).isPresent())return true;
        }
        return false;
    }
    public boolean collisionRegionAvailable(Box box) {
        if(box.minY<getWorld().getBottomY() || box.maxY>getWorld().getTopY()
            || !getWorld().getWorldBorder().contains(box))return false;
        int x0=MathHelper.floor(box.minX-1)>>4,x1=MathHelper.floor(box.maxX+1)>>4;
        int y0=MathHelper.floor(box.minY-1)>>4,y1=MathHelper.floor(box.maxY+1)>>4;
        int z0=MathHelper.floor(box.minZ-1)>>4,z1=MathHelper.floor(box.maxZ+1)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>216)return false;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(getWorld());
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(com.terminaldetector.drmd.world.access.CubicAvailability.withinHeight(getWorld(),new D6ChunkPos(x,y,z))
                && !access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static BodyBounds bounds(Box b){return new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ));}
    private static Box minecraft(BodyBounds b){return new Box(b.min().x(),b.min().y(),b.min().z(),b.max().x(),b.max().y(),b.max().z());}
    private static Vec3d minecraft(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
    private Box box(Cell cell) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for (Vec3 p:corners(cell,body.position(),body.rotation())) {
            minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
        }
        return new Box(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public boolean canCrossPortal(D6PortalTransform transform, Vec3 exit) {
        Vec3 previous = body.position(); Quat rotation = body.rotation();
        try {
            Quat turned=transform.transformOrientation(rotation);
            body.withPosition(exit.plus(turned.rotate(centre.minus(pivot)))).withRotation(turned);
            return !blocked();
        } finally { body.withPosition(previous).withRotation(rotation); }
    }
    public void crossPortal(D6PortalTransform transform) {
        body.withPosition(massPosition()); transform.apply(body, null);
        positionFromPhysics(); dataTracker.set(POSE,pose());
    }
    @Override public boolean canHit() { return true; }
    private static Vec3 pure(Vec3d v) { return new Vec3(v.x,v.y,v.z); }
}
