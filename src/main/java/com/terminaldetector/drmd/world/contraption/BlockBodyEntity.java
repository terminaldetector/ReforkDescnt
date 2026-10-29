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
    public static final int MAX_BLOCKS=32768,MAX_ASSEMBLY_EDGE=128;
    public static final int MIN_LOCAL=-32,MAX_LOCAL=128;
    public static final int PATCH_LIMIT=256;
    /** Refuse an atomic damage batch before mutation if it would flood the world with entities. */
    public static final int MAX_NEW_FRAGMENTS_PER_EDIT=256;
    public static boolean validLocal(BlockPos p){return p.getX()>=MIN_LOCAL&&p.getY()>=MIN_LOCAL&&p.getZ()>=MIN_LOCAL&&p.getX()<MAX_LOCAL&&p.getY()<MAX_LOCAL&&p.getZ()<MAX_LOCAL;}
    private static final TrackedData<NbtCompound> SHAPE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> PATCHES = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private long baseRevision;
    private NbtList shapePatches=new NbtList();
    private static final TrackedData<NbtCompound> POSE = DataTracker.registerData(BlockBodyEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final Set<Block> SUPPORTED = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.IRON_BLOCK,
        Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.OBSIDIAN, Blocks.GLASS, Blocks.BRICKS);
    private static final double MAX_LINEAR_SPEED = BodyStepBudget.MAX_LINEAR_SPEED;
    private static final double MAX_ANGULAR_SPEED = 2;
    private D6Volume<BlockState> volume = new D6Volume<>(Blocks.AIR.getDefaultState());
    private List<Cell> cells = List.of();
    private List<Cell> visibleCells=List.of();
    private Map<BlockPos,Integer> faceMasks=Map.of();
    public List<Cell> visibleCells(){return visibleCells;}
    public boolean visibleFace(BlockPos pos,Direction face){return (faceMasks.getOrDefault(pos,63)&(1<<face.getId()))!=0;}
    private Map<BlockPos,com.terminaldetector.drmd.world.geometry.HybridMesh> surfaces=Map.of();
    public com.terminaldetector.drmd.world.geometry.HybridMesh surface(Cell cell) {return surfaces.get(cell.pos());}
    private Map<BlockPos,BodyBounds> cellBounds=Map.of();
    private CompoundCollider collider=new CompoundCollider(List.of());
    private Map<Long,CompoundCollider> colliderSections=Map.of();
    private final AabbTree<BlockPos> blockIndex=new AabbTree<>();
    public Optional<CompoundCollider> colliderSection(BlockPos pos){return Optional.ofNullable(colliderSections.get(D6ChunkPos.ofBlock(pos.getX(),pos.getY(),pos.getZ()).asLong()));}
    public List<BlockPos> localCollisionCandidates(BodyBounds area){return blockIndex.query(area).values();}
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
    private boolean publishingPhysicsPosition;
    private boolean collisionUnavailable;
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
        passengerField(acceleration);passengerGravity=acceleration;dataTracker.set(POSE,pose());BodySpatialIndex.update(this);
    }
    public void setPhysicsGravity(boolean value) {gravity=value;dataTracker.set(POSE,pose());}
    public boolean physicsGravity() {return gravity;}
    public static boolean supports(BlockState state) {return SUPPORTED.contains(state.getBlock()) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.PLANKS);}

    public BlockBodyEntity(EntityType<? extends BlockBodyEntity> type, World world) {
        super(type, world); setNoGravity(true);
    }
    @Override public void setPosition(double x,double y,double z) {
        super.setPosition(x,y,z);
        if(body!=null) {
            if(!publishingPhysicsPosition) {
                // An explicit entity placement/teleport is a discontinuity, not a swept
                // path through all intervening chunks and other ships. Momentum is kept.
                body.withPosition(massPosition());previousPhysicsPosition=body.position();previousRotation=body.rotation();
            }
            BodySpatialIndex.update(this);
        }
    }
    /** One conservative envelope per hull, including its last contact pose and finite gravity field.
     * The client reserves the bounded32-command prediction window; no block/section entries. */
    public Optional<BodyBounds> spatialBounds() {
        if(cells.isEmpty())return Optional.empty();
        Vec3 at=massPosition(),start=getWorld().isClient?pure(getLerpedPos(0)).plus(body.rotation().rotate(centre.minus(pivot))):boardingMotionPaused()?at:previousPhysicsPosition;
        double reach=radius;
        if(passengerGravity.lengthSquared()>0) {
            // A sparse damaged hull's field is a padded local BOX. Its empty corners can lie
            // farther out than any material point, especially after a three-axis rotation.
            var local=passengerField(passengerGravity).localVolume();
            double x=Math.max(Math.abs(local.min().x()-centre.x()),Math.abs(local.max().x()-centre.x()));
            double y=Math.max(Math.abs(local.min().y()-centre.y()),Math.abs(local.max().y()-centre.y()));
            double z=Math.max(Math.abs(local.min().z()-centre.z()),Math.abs(local.max().z()-centre.z()));
            reach=Math.max(reach,Math.sqrt(x*x+y*y+z*z));
        }
        if(getWorld().isClient&&!remoteMotionPaused)reach+=MAX_LINEAR_SPEED*com.terminaldetector.drmd.network.BoardingNetworking.PREDICTION_HISTORY*.05;
        Vec3 r=new Vec3(reach,reach,reach);
        return Optional.of(new BodyBounds(start.minus(r),start.plus(r)).union(new BodyBounds(at.minus(r),at.plus(r))));
    }
    /** Full interior plus the next two physical ticks; loading does not use a material-only
     * sparse index or the client prediction margin. The plan adds its own two-block halo. */
    public BodyBounds loadingBounds() {
        var local=passengerGravity.lengthSquared()>0?passengerField(passengerGravity).localVolume():collider.bounds().orElseThrow();
        var current=OrientedBox.local(local,massPosition(),centre,rotation()).bounds();
        var predicted=new D6PhysicsBody().withLimits(MAX_LINEAR_SPEED,MAX_ANGULAR_SPEED).withMass(body.mass()).withInertia(body.inertia())
            .withPosition(massPosition()).withRotation(rotation()).withLinearVelocity(body.linearVelocity()).withAngularMomentum(body.angularMomentum());
        for(int i=0;i<2;i++){predicted.step(.05);current=current.union(OrientedBox.local(local,predicted.position(),centre,predicted.rotation()).bounds());}
        return current;
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SHAPE, new NbtCompound()); builder.add(PATCHES,new NbtCompound()); builder.add(POSE, new NbtCompound());
    }
    public List<Cell> cells() { return cells; }
    public Vec3 localCentre() { return centre; }
    public Vec3 localPivot(){return pivot;}
    public long shapeRevision(){return shapeRevision;}
    public Vec3 massPosition(){return pure(getPos()).plus(body.rotation().rotate(centre.minus(pivot)));}
    public Vec3 renderMassPosition(float delta){return pure(getLerpedPos(delta)).plus(renderRotation(delta).rotate(centre.minus(pivot)));}
    private void positionFromPhysics(){
        Vec3 at=body.position().plus(body.rotation().rotate(pivot.minus(centre)));
        publishingPhysicsPosition=true;try{setPosition(at.x(),at.y(),at.z());}finally{publishingPhysicsPosition=false;}
    }
    private NbtCompound shapeTag(){
        var n=BodyShapeCodec.write(cells);vector(n,"pivot",pivot);n.putLong("revision",shapeRevision);return n;
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
        BodySpatialIndex.update(this);
        if(!getWorld().isClient){dataTracker.set(POSE,pose());velocityModified=true;}
    }
    /** Physical contact/CCD correction in the same world. Terrain remains authoritative;
     * this never moves a passenger or rebuilds local geometry. */
    public boolean resolveContactPose(Vec3 position,Quat rotation) {
        if(!(getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())throw new IllegalStateException("Contact correction is server-owned");
        Vec3 before=body.position();Quat turn=body.rotation();body.withPosition(position).withRotation(rotation);
        if(blocked()){body.withPosition(before).withRotation(turn);return false;}
        positionFromPhysics();setBoundingBox(new Box(getPos(),getPos()).expand(radius));BodySpatialIndex.update(this);return true;
    }
    /** Complete the unelapsed part of a collision tick with the new contact velocity.
     * Frame force impulses were already applied by tick(); do not apply them twice. */
    public void advanceContactRemainder(double seconds) {
        if(!(getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!Double.isFinite(seconds)||seconds<0||seconds>.05)
            throw new IllegalArgumentException("one server collision remainder required");
        if(seconds<1e-10)return;
        int steps=Math.max(1,(int)Math.ceil(BodyStepBudget.steps(body,new Vec3(0,0,0),radius,false)*seconds/.05));
        for(int i=0;i<steps;i++) {
            Vec3 before=body.position(),velocity=body.linearVelocity(),momentum=body.angularMomentum();Quat q=body.rotation();body.step(seconds/steps);
            boolean collision=blocked();
            if(collisionUnavailable){body.withPosition(before).withRotation(q).withLinearVelocity(velocity).withAngularMomentum(momentum);physicsTick=Long.MIN_VALUE;break;}
            if(collision&&!(gravity&&gravityContacts(before,q,gravityDirection().scaled(-1)))) {
                body.withPosition(before).withRotation(q).withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));break;
            }
        }
        positionFromPhysics();setBoundingBox(new Box(getPos(),getPos()).expand(radius));BodySpatialIndex.update(this);
    }
    /** Current world pose plus the existing local BVH; no world blocks or chunk loads are read. */
    public BodyCollisionQuery collisionQuery() {
        return new BodyCollisionQuery(collider,massPosition(),centre,body.rotation());
    }

    private void loadShape(NbtCompound tag) {
        Vec3 oldPivot=pivot;var previous=new HashMap<BlockPos,BlockState>();for(var c:cells)previous.put(c.pos(),c.state());
        var nextVolume=new D6Volume<BlockState>(Blocks.AIR.getDefaultState());
        List<Cell> next = new ArrayList<>();
        D6MassProperties mass = new D6MassProperties();
        if(tag.getInt("format")==2)next.addAll(BodyShapeCodec.read(tag));
        else {
            var legacy=CubicBlockCodec.readVolume(tag,1000);
            for(long key:legacy.keys()) {
                var section=D6ChunkPos.fromLong(key);var cube=legacy.cube(section);
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    var state=cube.get(x,y,z);if(state.isAir())continue;
                    var pos=new BlockPos(section.minBlockX()+x,section.minBlockY()+y,section.minBlockZ()+z);
                    if(!supports(state)||next.size()>=MAX_BLOCKS||!validLocal(pos))throw new IllegalArgumentException("Unsupported or oversized body shape");
                    next.add(new Cell(pos,state));
                }
            }
        }
        for(var cell:next) {
            var pos=cell.pos();nextVolume.set(pos.getX(),pos.getY(),pos.getZ(),cell.state());
            mass.addBlock(new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5),blockMass(cell.state()));
        }
        volume=nextVolume;
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
        // Map.copyOf uses a compact open-addressed table. Regular BlockPos grids have many
        // equal/adjacent hashes and make that table pathological at ship scale. These maps
        // are privately owned snapshots; wrap their HashMap buckets without exposing writes.
        surfaces=Collections.unmodifiableMap(meshes);
        var masks=new HashMap<BlockPos,Integer>(faceMasks);masks.keySet().removeIf(p->!occupied.contains(p));
        for(var cell:cells) {
            if(dirty!=null&&!dirty.contains(cell.pos()))continue;
            int mask=0;
            for(var direction:Direction.values()) {
                var neighbour=cell.pos().offset(direction);var state=getLocalBlock(neighbour);
                // Smoothed neighbours do not necessarily cover the whole shared face.
                if(surfaces.containsKey(neighbour)||!state.isOpaque()&&!(cell.state().isOf(Blocks.GLASS)&&state.isOf(Blocks.GLASS)))mask|=1<<direction.getId();
            }
            masks.put(cell.pos(),mask);
        }
        faceMasks=Collections.unmodifiableMap(masks);
        visibleCells=cells.stream().filter(c->surface(c)!=null?!surface(c).faces().isEmpty():faceMasks.getOrDefault(c.pos(),63)!=0).toList();
        var perCell=new HashMap<BlockPos,BodyBounds>(cellBounds);
        for(var old:cellBounds.keySet())if(!occupied.contains(old))blockIndex.remove(old);
        perCell.keySet().removeIf(p->!occupied.contains(p));
        var dirtySections=new HashSet<Long>();
        if(dirty!=null)for(var p:dirty)dirtySections.add(D6ChunkPos.ofBlock(p.getX(),p.getY(),p.getZ()).asLong());
        for(Cell cell:cells) {
            if(dirty!=null&&!dirty.contains(cell.pos()))continue;
            BodyBounds bounds;
            if(surface(cell)==null)bounds=new BodyBounds(new Vec3(cell.pos().getX(),cell.pos().getY(),cell.pos().getZ()),
                new Vec3(cell.pos().getX()+1,cell.pos().getY()+1,cell.pos().getZ()+1));
            else {
                double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=-x0,z1=-x0;
                for(Vec3 v:surfaceCorners(cell,new Vec3(0,0,0),Quat.IDENTITY)) {
                    v=v.plus(centre);x0=Math.min(x0,v.x());y0=Math.min(y0,v.y());z0=Math.min(z0,v.z());
                    x1=Math.max(x1,v.x());y1=Math.max(y1,v.y());z1=Math.max(z1,v.z());
                }
                bounds=new BodyBounds(new Vec3(x0,y0,z0),new Vec3(x1,y1,z1));
            }
            perCell.put(cell.pos(),bounds);blockIndex.update(cell.pos(),bounds);
        }
        cellBounds=Collections.unmodifiableMap(perCell);
        localAim=cellBounds.values().stream().min(Comparator.comparingDouble(b->b.centre().minus(centre).lengthSquared())).map(BodyBounds::centre).orElse(centre);
        var sections=dirty==null?new HashMap<Long,CompoundCollider>():new HashMap<>(colliderSections);
        if(dirty==null)dirtySections.addAll(volume.keys());
        // Rebuild greedy solids and shaped primitives in touched 16-cubes only. The top-level
        // immutable directory is small; unchanged section BVHs survive edits by identity.
        for(long key:dirtySections) {
            var solid=new ArrayList<CompoundCollider.Cell>();var shaped=new ArrayList<BodyBounds>();
            var section=D6ChunkPos.fromLong(key);var cube=volume.cube(section);
            if(cube==null){sections.remove(key);continue;}
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                if(cube.get(x,y,z).isAir())continue;
                var pos=new BlockPos(section.minBlockX()+x,section.minBlockY()+y,section.minBlockZ()+z);
                if(surfaces.containsKey(pos))shaped.add(cellBounds.get(pos));
                else solid.add(new CompoundCollider.Cell(pos.getX(),pos.getY(),pos.getZ()));
            }
            shaped.addAll(CompoundCollider.cells(solid).boxes());sections.put(key,new CompoundCollider(shaped));
        }
        colliderSections=Map.copyOf(sections);
        collider=CompoundCollider.sections(new TreeMap<>(sections).values());

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
    public boolean setLocalBlock(BlockPos pos,BlockState state) {return setLocalBlocks(Map.of(pos,state));}
    /** Atomic damage/construction batch: one mass rebase and one rebuild per dirty section.
     * Validation completes before any grid mutation; no rigid body per block is introduced. */
    public boolean setLocalBlocks(Map<BlockPos,BlockState> edits) {
        if(!(getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())throw new IllegalStateException("Local edits are server-thread owned");
        if(isRemoved()||edits.size()>MAX_BLOCKS)return false;
        var changes=new ArrayList<LocalEdit>();int occupied=cells.size();
        for(var entry:edits.entrySet()) {
            var pos=entry.getKey();var state=entry.getValue();
            if(pos==null||state==null||!validLocal(pos)||!state.isAir()&&!supports(state))return false;
            var before=getLocalBlock(pos);if(before.equals(state))continue;
            occupied+=(before.isAir()?1:0)-(state.isAir()?1:0);changes.add(new LocalEdit(pos.toImmutable(),state));
        }
        if(changes.isEmpty()||occupied>MAX_BLOCKS)return false;
        changes.sort(Comparator.comparing((LocalEdit e)->!e.state().isAir()).thenComparingInt(e->e.pos().getX()).thenComparingInt(e->e.pos().getY()).thenComparingInt(e->e.pos().getZ()));
        if(!admissibleRebase(changes))return false;
        var prepared=prepareFracture(world,changes);if(prepared==null)return false;
        changes=new ArrayList<>(prepared.edits());
        var fragments=prepared.fragments();NbtCompound saved=fragments.isEmpty()?null:writeNbt(new NbtCompound());
        Vec3 savedPrevious=previousPhysicsPosition,savedTorque=pendingTorque;Quat savedTurn=previousRotation;long savedTick=physicsTick;
        try {
            for(var fragment:fragments)if(!world.spawnEntity(fragment)){fragments.forEach(BlockBodyEntity::discard);return false;}
        }catch(RuntimeException failure){fragments.forEach(BlockBodyEntity::discard);throw failure;}
        try {
            mutateLocalBlocks(changes);
            if(shapePatches.size()+changes.size()>=PATCH_LIMIT)resetShapeBaseline();
            else {
                for(var edit:changes) {
                    var patch=new NbtCompound();patch.putInt("pos",BodyShapeCodec.pack(edit.pos()));patch.put("state",NbtHelper.fromBlockState(edit.state()));shapePatches.add(patch);
                }
                var tag=new NbtCompound();tag.putLong("base",baseRevision);tag.putLong("revision",shapeRevision);tag.put("edits",shapePatches.copy());dataTracker.set(PATCHES,tag);
            }
            dataTracker.set(POSE,pose());velocityModified=true;
            if(cells.isEmpty())discard();return true;
        }catch(RuntimeException failure) {
            fragments.forEach(BlockBodyEntity::discard);
            if(saved!=null){readNbt(saved);previousPhysicsPosition=savedPrevious;previousRotation=savedTurn;pendingTorque=savedTorque;physicsTick=savedTick;publishPhysicsState();}
            throw failure;
        }
    }
    private record PreparedEdit(List<LocalEdit> edits,List<BlockBodyEntity> fragments){}
    private static BodyTopology.Cell topology(BlockPos p){return new BodyTopology.Cell(p.getX(),p.getY(),p.getZ());}
    /** Refuse before mutation when rebasing would require a velocity outside the admitted
     * collision budget. Returning false preserves material and momentum instead of clipping it. */
    private boolean admissibleRebase(List<LocalEdit> edits) {
        double mass=body.mass();Vec3 moment=centre.scaled(mass);
        for(var edit:edits) {
            var old=getLocalBlock(edit.pos());double delta=(edit.state().isAir()?0:blockMass(edit.state()))-(old.isAir()?0:blockMass(old));
            mass+=delta;moment=moment.plus(new Vec3(edit.pos().getX()+.5,edit.pos().getY()+.5,edit.pos().getZ()+.5).scaled(delta));
        }
        if(mass<1e-9)return true;
        Vec3 shift=body.rotation().rotate(moment.scaled(1/mass).minus(centre));
        return body.linearVelocity().plus(body.angularVelocity().cross(shift)).length()<=MAX_LINEAR_SPEED+1e-8;
    }
    private PreparedEdit prepareFracture(ServerWorld world,List<LocalEdit> edits) {
        if(edits.stream().noneMatch(e->e.state().isAir()))return new PreparedEdit(edits,List.of());
        // A removed leaf cannot break a connection. Keep the ordinary one-block chip cheap.
        if(edits.size()==1) {
            int neighbours=0;for(var direction:Direction.values())if(!getLocalBlock(edits.getFirst().pos().offset(direction)).isAir())neighbours++;
            if(neighbours<2)return new PreparedEdit(edits,List.of());
        }
        var before=new HashSet<BodyTopology.Cell>();var next=new HashMap<BlockPos,Cell>();
        for(var cell:cells){before.add(topology(cell.pos()));next.put(cell.pos(),cell);}
        var removed=new HashSet<BodyTopology.Cell>();
        for(var edit:edits)if(edit.state().isAir()){removed.add(topology(edit.pos()));next.remove(edit.pos());}else next.put(edit.pos(),new Cell(edit.pos(),edit.state()));
        var after=new HashSet<BodyTopology.Cell>();for(var pos:next.keySet())after.add(topology(pos));
        var topology=BodyTopology.afterRemoval(before,after,removed);
        if(topology.detached().isEmpty())return new PreparedEdit(edits,List.of());
        if(topology.detached().size()>MAX_NEW_FRAGMENTS_PER_EDIT)return null;
        var parentEdits=new HashMap<BlockPos,BlockState>();for(var edit:edits)parentEdits.put(edit.pos(),edit.state());
        var fragments=new ArrayList<BlockBodyEntity>();
        for(var component:topology.detached()) {
            var fragmentCells=new ArrayList<Cell>();
            for(var cell:component) {
                var pos=new BlockPos(cell.x(),cell.y(),cell.z());fragmentCells.add(next.get(pos));parentEdits.put(pos,Blocks.AIR.getDefaultState());
            }
            fragmentCells.sort(Comparator.comparingInt((Cell c)->c.pos().getX()).thenComparingInt(c->c.pos().getY()).thenComparingInt(c->c.pos().getZ()));
            var fragment=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);fragment.loadShape(BodyShapeCodec.write(fragmentCells));
            fragment.body.withRotation(body.rotation());
            Vec3 shift=body.rotation().rotate(fragment.centre.minus(centre)),position=massPosition().plus(shift);
            fragment.setPosition(position.x(),position.y(),position.z());
            Vec3 velocity=body.linearVelocity().plus(body.angularVelocity().cross(shift));
            if(velocity.length()>MAX_LINEAR_SPEED+1e-8)return null;
            fragment.body.withLinearVelocity(velocity).withAngularVelocity(body.angularVelocity());
            for(var cell:fragmentCells)if(materialDamage.containsKey(cell.pos()))fragment.materialDamage.put(cell.pos(),materialDamage.get(cell.pos()));
            fragment.gravity=gravity; // Artificial gravity stays with the parent until ControlCore ownership exists.
            fragment.resetShapeBaseline();fragment.dataTracker.set(POSE,fragment.pose());
            fragment.setBoundingBox(new Box(fragment.getPos(),fragment.getPos()).expand(fragment.radius));fragments.add(fragment);
        }
        var finalEdits=new ArrayList<LocalEdit>();
        for(var edit:parentEdits.entrySet())if(!getLocalBlock(edit.getKey()).equals(edit.getValue()))finalEdits.add(new LocalEdit(edit.getKey(),edit.getValue()));
        finalEdits.sort(Comparator.comparing((LocalEdit e)->!e.state().isAir()).thenComparingInt(e->e.pos().getX()).thenComparingInt(e->e.pos().getY()).thenComparingInt(e->e.pos().getZ()));
        if(!admissibleRebase(finalEdits))return null;
        return new PreparedEdit(finalEdits,fragments);
    }
    private void resetShapeBaseline() {
        baseRevision=shapeRevision;shapePatches=new NbtList();
        dataTracker.set(SHAPE,shapeTag());dataTracker.set(PATCHES,new NbtCompound());
    }
    private record LocalEdit(BlockPos pos,BlockState state) {}
    /** One received journal batch rebuilds each dirty section once. Mass rebasing preserves the
     * same material velocity field as sequential edits, without N full hull rebuilds on join. */
    private void mutateLocalBlocks(List<LocalEdit> changes) {
        if(changes.isEmpty())return;
        var next=new HashMap<BlockPos,Cell>();for(var cell:cells)next.put(cell.pos(),cell);
        for(var edit:changes) {
            var before=next.get(edit.pos());
            if(!validLocal(edit.pos())||!edit.state().isAir()&&!supports(edit.state())
                ||(before==null?edit.state().isAir():before.state().equals(edit.state())))throw new IllegalArgumentException("invalid body patch state");
            if(edit.state().isAir())next.remove(edit.pos());else next.put(edit.pos(),new Cell(edit.pos().toImmutable(),edit.state()));
            if(next.size()>MAX_BLOCKS)throw new IllegalArgumentException("body patch block budget");
        }
        Vec3 origin=massPosition(),spin=body.angularVelocity(),velocity=body.linearVelocity(),oldCentre=centre;
        var dirty=new HashSet<BlockPos>();
        for(var edit:changes) {
            var pos=edit.pos();var before=getLocalBlock(pos);var point=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            if(!before.isAir())massProperties.addBlock(point,-blockMass(before));
            if(!edit.state().isAir())massProperties.addBlock(point,blockMass(edit.state()));
            volume.set(pos.getX(),pos.getY(),pos.getZ(),edit.state());materialDamage.remove(pos);markGeometryNeighbours(dirty,pos);
        }
        var ordered=new ArrayList<>(next.values());ordered.sort(Comparator.comparingInt((Cell c)->c.pos().getX()).thenComparingInt(c->c.pos().getY()).thenComparingInt(c->c.pos().getZ()));
        cells=List.copyOf(ordered);centre=massProperties.centreOfMass();
        Vec3 shift=body.rotation().rotate(centre.minus(oldCentre));
        body.withMassProperties(massProperties).withPosition(origin.plus(shift))
            .withLinearVelocity(velocity.plus(spin.cross(shift))).withAngularVelocity(spin);
        previousPhysicsPosition=previousPhysicsPosition.plus(previousRotation.rotate(centre.minus(oldCentre)));
        rebuildGeometry(dirty);shapeRevision+=changes.size();
        setBoundingBox(new Box(getPos(),getPos()).expand(radius));BodySpatialIndex.update(this);
    }
    private void receiveShape(boolean baselineChanged) {
        Vec3 before=centre;
        if(baselineChanged) {
            var tag=dataTracker.get(SHAPE);loadShape(tag);baseRevision=shapeRevision;
            body.withPosition(massPosition());
        }
        var patches=dataTracker.get(PATCHES);
        if(patches.contains("base")&&patches.getLong("base")==baseRevision) {
            var edits=patches.getList("edits",NbtElement.COMPOUND_TYPE);
            if(edits.size()>=PATCH_LIMIT||patches.getLong("revision")!=baseRevision+edits.size())throw new IllegalArgumentException("invalid body patch journal");
            int begin=(int)(shapeRevision-baseRevision);
            if(begin<0||begin>edits.size())throw new IllegalArgumentException("body patch revision regression");
            var changes=new ArrayList<LocalEdit>();
            for(int i=begin;i<edits.size();i++) {
                var edit=edits.getCompound(i);var pos=BodyShapeCodec.unpack(edit.getInt("pos"));
                var state=NbtHelper.toBlockState(net.minecraft.registry.Registries.BLOCK.getReadOnlyWrapper(),edit.getCompound("state"));
                changes.add(new LocalEdit(pos,state));
            }
            mutateLocalBlocks(changes);
        }
        setBoundingBox(new Box(getPos(),getPos()).expand(radius));BodySpatialIndex.update(this);clientShapeListener.accept(this,before);
    }
    private static double blockMass(BlockState state){return state.isIn(BlockTags.LEAVES)?.08:1;}
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (getWorld().isClient && body != null) {
            if (SHAPE.equals(data)||PATCHES.equals(data))receiveShape(SHAPE.equals(data));
            if (POSE.equals(data)) {
                previousRotation = body.rotation(); readPose(dataTracker.get(POSE));
                displayRotation.receive(body.rotation(),age);
            }
            BodySpatialIndex.update(this);
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
        materialDamage.clear();var damage=n.getList("materialDamage",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(damage.size(),MAX_BLOCKS);i++) {
            var entry=damage.getCompound(i);var pos=BodyShapeCodec.unpack(entry.getInt("pos"));double amount=entry.getDouble("damage");
            if(validLocal(pos)&&!getLocalBlock(pos).isAir()&&Double.isFinite(amount)&&amount>0&&amount<materialStrength(pos))materialDamage.put(pos,amount);
        }
        body.withPosition(massPosition()); previousRotation = body.rotation();
        displayRotation.reset(body.rotation(),age);
        resetShapeBaseline(); dataTracker.set(POSE, pose());BodySpatialIndex.update(this);
    }
    @Override protected void writeCustomDataToNbt(NbtCompound n) {
        n.put("shape", shapeTag()); n.put("body", pose());
        var damage=new NbtList();
        materialDamage.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var cell=new NbtCompound();cell.putInt("pos",BodyShapeCodec.pack(entry.getKey()));cell.putDouble("damage",entry.getValue());damage.add(cell);});
        n.put("materialDamage",damage);
    }
    public static BlockBodyEntity assemble(ServerWorld world, BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        if (max.getX() - min.getX() >= MAX_ASSEMBLY_EDGE || max.getY() - min.getY() >= MAX_ASSEMBLY_EDGE || max.getZ() - min.getZ() >= MAX_ASSEMBLY_EDGE)
            throw new IllegalArgumentException("Selection must fit 128 x 128 x 128");
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.iterate(min, max)) {
            BlockPos p = cursor.toImmutable();
            var snapshot=com.terminaldetector.drmd.world.access.WorldAccess.of(world).snapshotIfLoaded(p);
            if(snapshot.isEmpty() || !world.getWorldBorder().contains(p) || world.isOutOfHeightLimit(p))
                throw new IllegalArgumentException("Selection must be inside loaded world bounds");
            BlockState state=snapshot.get().state();
            if (state.isAir()) continue;
            if (!supports(state) || world.getBlockEntity(p) != null || original.size() >= MAX_BLOCKS)
                throw new IllegalArgumentException("Up to 32768 supported inert blocks, including logs, leaves and planks");
            original.put(p, state);
        }
        if (original.isEmpty()) throw new IllegalArgumentException("Empty selection");
        return assembleSelection(world,original);
    }
    /** Local blueprint construction for the existing BlockBody runtime. No temporary world
     * copy, terrain scan, dimension or rigid body per block is involved. */
    public static BlockBodyEntity createLocal(ServerWorld world,List<Cell> cells,Vec3d gridOrigin) {
        BodyBounds.finite(pure(gridOrigin));
        if(!world.getServer().isOnThread()||cells.isEmpty())throw new IllegalArgumentException("nonempty server-owned body required");
        var entity=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);
        entity.loadShape(BodyShapeCodec.write(cells));
        entity.setPosition(gridOrigin.x+entity.centre.x(),gridOrigin.y+entity.centre.y(),gridOrigin.z+entity.centre.z());
        entity.body.withPosition(pure(entity.getPos()));entity.resetShapeBaseline();entity.dataTracker.set(POSE,entity.pose());
        if(!world.spawnEntity(entity))throw new IllegalStateException("Could not spawn body");
        return entity;
    }
    /** Exact sparse selection: neighbouring machines and terrain outside the selection stay untouched. */
    public static BlockBodyEntity assembleSelection(ServerWorld world,Map<BlockPos,BlockState> original) {
        if(original.isEmpty() || original.size()>MAX_BLOCKS)throw new IllegalArgumentException("Select 1..32768 inert blocks");
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
                || local.getX()>=MAX_ASSEMBLY_EDGE || local.getY()>=MAX_ASSEMBLY_EDGE || local.getZ()>=MAX_ASSEMBLY_EDGE)
                throw new IllegalArgumentException("Unsupported, changed, unloaded or oversized selection");
            shape.set(local.getX(),local.getY(),local.getZ(),state);
        }
        BlockBodyEntity entity = new BlockBodyEntity(ModEntities.BLOCK_BODY, world);
        entity.loadShape(CubicBlockCodec.writeVolume(shape));
        entity.setPosition(min.getX()+entity.centre.x(), min.getY()+entity.centre.y(), min.getZ()+entity.centre.z());
        entity.body.withPosition(pure(entity.getPos()));
        entity.resetShapeBaseline();
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
        // Entity.baseTick performs fluid scans over getBoundingBox(). That box is a render/
        // selection envelope, NOT a solid ship. Keep vanilla entity bookkeeping on its pivot
        // proxy; all hull collision and movement use the section geometry below.
        Box envelope=getBoundingBox();setBoundingBox(new Box(getPos(),getPos()).expand(.5));
        try{super.tick();}finally{setBoundingBox(envelope);}
        if (getWorld().isClient) { setBoundingBox(new Box(getPos(), getPos()).expand(radius));BodySpatialIndex.update(this); return; }
        if (cells.isEmpty()) return;
        body.withPosition(massPosition()); previousRotation = body.rotation();
        previousPhysicsPosition=body.position();physicsTick=getWorld().getTime();
        // Substeps bound corner travel to about 0.15 blocks, including rotation. Inelastic contact.
        int steps=BodyStepBudget.steps(body,pendingTorque,radius,gravity);
        Vec3 externalTorque=pendingTorque;pendingTorque=new Vec3(0,0,0);
        for (int step = 0; step < steps; step++) {
            Vec3 before = body.position(); Quat rotation = body.rotation();
            Vec3 velocityBefore=body.linearVelocity(),momentumBefore=body.angularMomentum();
            Vec3 gravityDown = gravityDirection();
            if(gravity)body.applyForce(gravityDown.scaled(9.81*body.mass()));
            // D6PhysicsBody clears force/torque after every integration step. Reapply the frame's
            // external torque to each collision substep so its total impulse remains torque*0.05,
            // independent of body speed/radius and therefore of the chosen substep count.
            body.applyTorque(externalTorque);
            body.step(.05 / steps);
            boolean collision=blocked();
            if(collisionUnavailable) {
                // Unavailable terrain is not an impact. Pause with momentum intact; no
                // synchronous generation or fake collision may destroy a ship's velocity.
                body.withPosition(before).withRotation(rotation).withLinearVelocity(velocityBefore).withAngularMomentum(momentumBefore);
                physicsTick=Long.MIN_VALUE;break;
            }
            if (collision && !(gravity && gravityContacts(before,rotation,gravityDown.scaled(-1)))) {
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
    /** Server-owned partial damage belongs to local material, never world block coordinates. */
    private final Map<BlockPos,Double> materialDamage = new HashMap<>();
    public double materialDamage(BlockPos pos){return materialDamage.getOrDefault(pos,0.);}
    private double materialStrength(BlockPos pos) {
        // Supported inert materials have position-independent hardness. Never query world state
        // at a local-grid coordinate: the ship may be rotated or outside vanilla world height.
        float hardness=getLocalBlock(pos).getHardness(net.minecraft.world.EmptyBlockView.INSTANCE,pos);
        return hardness<0?Double.POSITIVE_INFINITY:Math.max(4,hardness*12.);
    }
    @Override public void weaponHit(Vec3d impact,Vec3d incoming,float damage) {
        if(!(getWorld() instanceof ServerWorld)||!Float.isFinite(damage)||damage<=0
            ||!Double.isFinite(impact.lengthSquared())||!Double.isFinite(incoming.lengthSquared())||incoming.lengthSquared()<1e-12)return;
        PhysicsTarget.super.weaponHit(impact,incoming,damage);
        var direction=incoming.normalize();
        var hit=new BlockBodyWorldAccess(this).raycastBlockBody(impact.subtract(direction.multiply(.001)),impact.add(direction.multiply(.001)));
        hit.ifPresent(h->damageLocalMaterial(Map.of(h.localBlock(),(double)damage)));
    }
    @Override public void weaponBlast(Vec3d origin,float damage,double radius) {
        if(!(getWorld() instanceof ServerWorld)||!Float.isFinite(damage)||damage<=0||!Double.isFinite(radius)||radius<=0
            ||!Double.isFinite(origin.lengthSquared()))return;
        Vec3d local=worldToLocal(origin);Vec3 a=new Vec3(local.x-radius,local.y-radius,local.z-radius),b=new Vec3(local.x+radius,local.y+radius,local.z+radius);
        var loads=new HashMap<BlockPos,Double>();Vec3d closest=null;double nearest=radius;
        for(var pos:localCollisionCandidates(new BodyBounds(a,b))) {
            // Exact sphere/unit-box distance in the rotated grid, not distance to the hull COM.
            var point=new Vec3d(MathHelper.clamp(local.x,pos.getX(),pos.getX()+1),MathHelper.clamp(local.y,pos.getY(),pos.getY()+1),MathHelper.clamp(local.z,pos.getZ(),pos.getZ()+1));
            double distance=point.distanceTo(local);if(distance>=radius)continue;
            loads.put(pos,(double)damage*(1-distance/radius));
            if(closest==null||distance<nearest){nearest=distance;closest=localToWorld(point);}
        }
        if(closest==null)return;
        Vec3d direction=closest.subtract(origin);if(direction.lengthSquared()<1e-8)direction=physicsCentre().subtract(origin);
        if(direction.lengthSquared()<1e-8)direction=new Vec3d(0,1,0);
        applyPhysicsImpulse(closest,direction.normalize().multiply(Math.min(80,damage*(1-nearest/radius)*.15)));
        damageLocalMaterial(loads);
    }
    /** One blast performs one atomic topology/mass/collider edit; no item entity per lost cell. */
    private void damageLocalMaterial(Map<BlockPos,Double> loads) {
        var next=new HashMap<BlockPos,Double>();var edits=new HashMap<BlockPos,BlockState>();
        for(var load:loads.entrySet()) {
            var pos=load.getKey();if(getLocalBlock(pos).isAir()||!Double.isFinite(load.getValue())||load.getValue()<=0)continue;
            double total=materialDamage(pos)+load.getValue();
            if(total>=materialStrength(pos))edits.put(pos,Blocks.AIR.getDefaultState());else next.put(pos,total);
        }
        // A refused fragment/speed admission cannot partially consume material damage either.
        if(edits.isEmpty()){materialDamage.putAll(next);return;}
        var before=new HashMap<>(materialDamage);materialDamage.putAll(next);
        try {
            if(!edits.isEmpty()&&!setLocalBlocks(edits)){materialDamage.clear();materialDamage.putAll(before);}
        }catch(RuntimeException failure){materialDamage.clear();materialDamage.putAll(before);throw failure;}
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
        collisionUnavailable=false;
        Quat inverse=body.rotation().inverse();
        // Visit occupied section envelopes rather than the entire (possibly mostly empty)
        // ship AABB. A long sparse hull must not scan millions of intervening world blocks.
        for(var section:colliderSections.values()) {
            var localBounds=section.bounds().orElseThrow();
            Box broad=minecraft(OrientedBox.local(localBounds,body.position(),centre,body.rotation()).bounds());
            if(!collisionRegionAvailable(broad)){collisionUnavailable=true;return true;}
            for(var shape:getWorld().getBlockCollisions(this,broad.contract(1e-6)))for(Box obstacle:shape.getBoundingBoxes()) {
                BodyBounds worldBox=bounds(obstacle);
                var localObstacle=new OrientedBox(inverse.rotate(worldBox.centre().minus(body.position())).plus(centre),worldBox.half(),inverse);
                for(BodyBounds solid:section.query(localObstacle.bounds()))
                    if(OrientedBox.axisAligned(solid).contact(localObstacle,0)
                        .filter(contact->contact.penetration()>1e-6).isPresent())return true;
            }
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
        // Portal discontinuities are not straight trajectories through intervening terrain.
        previousPhysicsPosition=body.position();previousRotation=body.rotation();
        positionFromPhysics(); dataTracker.set(POSE,pose());
    }
    @Override public boolean canHit() { return true; }
    private static Vec3 pure(Vec3d v) { return new Vec3(v.x,v.y,v.z); }
}
