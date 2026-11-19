package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.physics.PhysicsProfileState;
import com.terminaldetector.drmd.physics.collision.BodyTopology;
import com.terminaldetector.drmd.physics.collision.UnsupportedStructure;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import java.util.*;
import java.util.function.Predicate;

/** Small unsupported engineered fragments after a real destructive blast; trees keep their adapter. */
public final class BlastStructuralCollapse {
    public static final int MAX_COMPONENT=256,MAX_CUTS=128,MAX_READS=4096,MAX_BODIES=4,MAX_NEARBY=64;
    // Half of the per-tick read ceiling remains available for the complete support query.
    private static final int CAPTURE_SCAN=MAX_READS/2,RADIUS=16;
    private static final Set<Block> INERT=Set.of(Blocks.COBBLESTONE,Blocks.MOSSY_COBBLESTONE,Blocks.IRON_BLOCK,Blocks.GOLD_BLOCK,
        Blocks.DIAMOND_BLOCK,Blocks.OBSIDIAN,Blocks.GLASS,Blocks.BRICKS,Blocks.OXIDIZED_COPPER,
        Blocks.PRISMARINE_BRICKS,Blocks.DARK_PRISMARINE,Blocks.CYAN_STAINED_GLASS,Blocks.SEA_LANTERN,
        Blocks.MUD_BRICKS,Blocks.DEEPSLATE_BRICKS,Blocks.CRACKED_DEEPSLATE_BRICKS,Blocks.DEEPSLATE_TILES,
        Blocks.SANDSTONE,Blocks.CUT_SANDSTONE,Blocks.SMOOTH_SANDSTONE,Blocks.RED_SANDSTONE,
        Blocks.CUT_RED_SANDSTONE,Blocks.SMOOTH_RED_SANDSTONE);
    private static final Map<ServerWorld,Budget> BUDGETS=new WeakHashMap<>();
    private static final class Budget {
        final long tick;int reads,bodies;
        Budget(long tick){this.tick=tick;}
        boolean read(){if(reads>=MAX_READS)return false;reads++;return true;}
    }
    private BlastStructuralCollapse(){}
    private static Budget budget(ServerWorld world){
        var b=BUDGETS.get(world);if(b==null||b.tick!=world.getTime()){b=new Budget(world.getTime());BUDGETS.put(world,b);}return b;
    }
    private static boolean enabled(ServerWorld world){return world.getServer().isOnThread()&&PhysicsProfileState.expanded(world);}
    static boolean buildingMaterial(BlockState state){
        if(state.hasBlockEntity()||!state.getFluidState().isEmpty()||state.isAir())return false;
        if(INERT.contains(state.getBlock())||state.isIn(BlockTags.PLANKS)||partialMaterial(state))return true;
        // Ordinary masonry and decorative full blocks can be used in any building.
        // Bulk stone, soil and sediment keep anchoring the surviving structure to the world.
        var empty=net.minecraft.world.EmptyBlockView.INSTANCE;
        return !state.isIn(BlockTags.BASE_STONE_OVERWORLD)&&!state.isIn(BlockTags.BASE_STONE_NETHER)
            &&!state.isIn(BlockTags.DIRT)&&!state.isIn(BlockTags.SAND)&&!state.isOf(Blocks.GRAVEL)
            &&!state.isIn(BlockTags.LOGS)&&!state.isIn(BlockTags.LEAVES)&&state.getHardness(empty,BlockPos.ORIGIN)>=0
            &&state.isFullCube(empty,BlockPos.ORIGIN);
    }
    static boolean partialMaterial(BlockState state){
        return state.isIn(BlockTags.SLABS)||state.isIn(BlockTags.STAIRS)||state.isIn(BlockTags.WALLS)
            ||state.isIn(BlockTags.FENCES)||state.isIn(BlockTags.FENCE_GATES);
    }
    private static boolean inert(BlockState state){
        return buildingMaterial(state) || state.isIn(BlockTags.LOGS) && state.getFluidState().isEmpty();
    }
    /** Material resistance is shared by direct cuts and the pressure that reaches blast cells. */
    public static boolean canBreak(ServerWorld world,BlockPos pos,BlockState state,double pressure){
        float hardness=state.getHardness(world,pos);
        return !state.isAir() && hardness>=0 && Double.isFinite(pressure)
            && pressure>=Math.max(12,Math.max(hardness*12,state.getBlock().getBlastResistance()*4));
    }
    public static void impact(ServerWorld world,BlockPos pos,Vec3d impact,float damage,Entity attacker){
        if(!enabled(world)||!WorldEditSafety.mayChange(world,pos,attacker))return;
        var state=WorldAccess.of(world).readIfLoaded(pos);
        // Log beams that belong to a building can fail the tree-specific cut.
        // They are still load-bearing material for the ordinary structural solver.
        if(state.isEmpty()||!inert(state.get())||!canBreak(world,pos,state.get(),damage))return;
        if(world.breakBlock(pos,true,attacker))finish(world,List.of(pos),impact,damage/24,
            p->WorldEditSafety.mayChange(world,p,attacker));
    }
    /** Snapshot only the blast's own admitted inert material, before vanilla or microdamage edits it. */
    public static List<BlockPos> capture(ServerWorld world,List<BlockPos> affected){
        if(!enabled(world))return List.of();var b=budget(world);if(b.bodies>=MAX_BODIES)return List.of();
        var cuts=new LinkedHashSet<BlockPos>();var access=WorldAccess.of(world);
        for(int i=0;i<Math.min(CAPTURE_SCAN,affected.size())&&cuts.size()<MAX_CUTS;i++){
            if(!b.read())break;var p=affected.get(i);var state=access.readIfLoaded(p);
            if(state.isPresent()&&inert(state.get()))cuts.add(p.toImmutable());
        }
        return List.copyOf(cuts);
    }
    /** No guessed cuts: formerly admitted material must now be empty or replaceable (e.g. blast fire). */
    public static int finish(ServerWorld world,List<BlockPos> cuts,Vec3d centre,float power){
        return finish(world,cuts,centre,power,p->com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(world,p,null));
    }
    public static int finish(ServerWorld world,List<BlockPos> cuts,Vec3d centre,float power,Predicate<BlockPos> mayDetach){
        if(!enabled(world)||cuts.isEmpty()||cuts.size()>MAX_CUTS||!Float.isFinite(power)||power<=0
            ||!Double.isFinite(centre.lengthSquared()))return 0;
        var b=budget(world);if(b.bodies>=MAX_BODIES)return 0;
        if(BodySpatialIndex.query(world,new Box(centre,centre).expand(32)).stream().filter(BlockBodyEntity::physicsGravity).limit(MAX_NEARBY).count()>=MAX_NEARBY)return 0;
        Objects.requireNonNull(mayDetach);
        var read=new Reader(world,b,mayDetach);var considered=new HashSet<BodyTopology.Cell>();int created=0;
        for(var cut:cuts){
            var removed=read.state(cut);if(removed.isEmpty()||!empty(removed.get()))continue;
            for(var d:Direction.values()){
                if(b.bodies>=MAX_BODIES||b.reads>=MAX_READS)return created;
                var seed=cell(cut.offset(d));if(!considered.add(seed))continue;
                var probe=UnsupportedStructure.examine(seed,read::contact,MAX_COMPONENT,RADIUS);considered.addAll(probe.cells());
                if(probe.status()!=UnsupportedStructure.Status.DETACHED||probe.cells().size()<2)continue;
                var selected=new LinkedHashMap<BlockPos,BlockState>();
                for(var c:probe.cells()){var p=pos(c);selected.put(p,read.state(p).orElseThrow());}
                BlockBodyEntity body;
                try{body=BlockBodyEntity.assembleSelection(world,selected);}
                catch(RuntimeException failure){
                    // An assembler rejection may be contained only after its complete rollback is observed.
                    if(selected.entrySet().stream().allMatch(e->WorldAccess.of(world).readIfLoaded(e.getKey()).filter(e.getValue()::equals).isPresent()))continue;
                    throw failure;
                }
                b.bodies++;created++;body.setPhysicsGravity(true);
                var direction=body.getBoundingBox().getCenter().subtract(centre);
                if(direction.lengthSquared()<1e-8)direction=new Vec3d(1,0,0);
                var point=selected.keySet().stream().min(Comparator.comparingDouble(p->p.toCenterPos().squaredDistanceTo(centre))).orElseThrow().toCenterPos();
                body.weaponImpulse(point,direction,(float)(Math.min(16,power)*24));
                // Cached pre-transfer cells cannot seed a second body in this same explosion.
                for(var p:selected.keySet())read.cache.put(p,Optional.of(Blocks.AIR.getDefaultState()));
            }
        }
        return created;
    }
    private static BodyTopology.Cell cell(BlockPos p){return new BodyTopology.Cell(p.getX(),p.getY(),p.getZ());}
    private static BlockPos pos(BodyTopology.Cell c){return new BlockPos(c.x(),c.y(),c.z());}
    private static boolean empty(BlockState state){return state.isAir()||state.isReplaceable()||state.isIn(BlockTags.LEAVES);}
    private static final class Reader {
        final ServerWorld world;final Budget budget;final Predicate<BlockPos> permission;final Map<BlockPos,Optional<BlockState>> cache=new HashMap<>();
        Reader(ServerWorld world,Budget budget,Predicate<BlockPos> permission){this.world=world;this.budget=budget;this.permission=permission;}
        Optional<BlockState> state(BlockPos p){
            var known=cache.get(p);if(known!=null)return known;
            if(!budget.read())return Optional.empty();
            var value=world.getWorldBorder().contains(p)?WorldAccess.of(world).readIfLoaded(p):Optional.<BlockState>empty();
            cache.put(p.toImmutable(),value);return value;
        }
        UnsupportedStructure.Contact contact(BodyTopology.Cell cell){
            var state=state(pos(cell));return state.isEmpty()?UnsupportedStructure.Contact.UNKNOWN
                :empty(state.get())?UnsupportedStructure.Contact.EMPTY:inert(state.get())&&permission.test(pos(cell))?UnsupportedStructure.Contact.MATERIAL:UnsupportedStructure.Contact.ANCHOR;
        }
    }
}
