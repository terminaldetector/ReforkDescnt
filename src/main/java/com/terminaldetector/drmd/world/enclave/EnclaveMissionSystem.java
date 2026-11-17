package com.terminaldetector.drmd.world.enclave;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.Point;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.Type;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionLedger.Outcome;

/** Actual server events bridge the existing save-owned protocol; it never edits terrain. */
public final class EnclaveMissionSystem {
    private static final int MAX_CUBES=27;
    private static final PersistentState.Type<FactionMemory> MEMORY_TYPE=new PersistentState.Type<>(FactionMemory::new,FactionMemory::fromNbt,null);
    private static boolean registered;
    private EnclaveMissionSystem(){}
    private static boolean connected(ServerWorld world,ServerPlayerEntity player){
        return world.getServer().isOnThread()&&player.getServerWorld()==world&&player.isAlive()&&!player.isRemoved()
            &&player.networkHandler!=null&&world.getServer().getPlayerManager().getPlayer(player.getUuid())==player
            &&world.getEntity(player.getUuid())==player&&Double.isFinite(player.getX())&&Double.isFinite(player.getY())&&Double.isFinite(player.getZ());
    }
    private static boolean survivor(ServerWorld world,ServerPlayerEntity player){
        return connected(world,player)&&player.interactionManager.getGameMode()==GameMode.SURVIVAL&&!player.isCreative()
            &&!player.isSpectator()&&player.getAbilities().allowModifyWorld;
    }
    private static boolean author(ServerPlayerEntity player){
        return connected(player.getServerWorld(),player)&&player.isCreative()&&!player.isSpectator()
            &&player.hasPermissionLevel(2)&&player.getAbilities().allowModifyWorld;
    }
    private static Point point(BlockPos pos){return new Point(pos.getX(),pos.getY(),pos.getZ());}
    private static Point position(ServerPlayerEntity player){return point(player.getBlockPos());}
    private static BlockPos block(Point pos){return new BlockPos(pos.x(),pos.y(),pos.z());}
    private static EnclaveMissionLedger.Context context(ServerWorld world){
        var backend=WorldAccess.of(world).backend();
        if(backend==CubicAvailability.Backend.UNSUPPORTED)throw new IllegalArgumentException("Unsupported world backend");
        return new EnclaveMissionLedger.Context(world.getRegistryKey().getValue().toString(),world.getSeed(),backend.name(),world.getBottomY(),world.getTopY());
    }
    private static void permitted(ServerWorld world,ServerPlayerEntity player,Point point){
        var pos=block(point);
        if(!WorldEditSafety.mayChange(world,pos,player))throw new IllegalArgumentException("Unavailable, outside bounds or protected mission position: "+pos);
    }
    private static void cube(Set<D6ChunkPos> cubes,Point point){
        cubes.add(D6ChunkPos.ofBlock(point.x(),point.y(),point.z()));
        if(cubes.size()>MAX_CUBES)throw new IllegalArgumentException("Compact mission volume cap27");
    }
    private static void geometry(ServerWorld world,ServerPlayerEntity player,Point herald,Point anchor,List<EnclaveMissionContract.Step> steps){
        var cubes=new HashSet<D6ChunkPos>();permitted(world,player,herald);permitted(world,player,anchor);cube(cubes,herald);cube(cubes,anchor);
        for(var step:steps){
            if(step.type()==Type.BREAK_BLOCK){permitted(world,player,step.block());cube(cubes,step.block());continue;}
            var box=step.volume();var last=new Point(box.max().x()-1,box.max().y()-1,box.max().z()-1);
            permitted(world,player,box.min());permitted(world,player,last);
            for(int x=Math.floorDiv(box.min().x(),16);x<=Math.floorDiv(last.x(),16);x++)
                for(int y=Math.floorDiv(box.min().y(),16);y<=Math.floorDiv(last.y(),16);y++)
                    for(int z=Math.floorDiv(box.min().z(),16);z<=Math.floorDiv(last.z(),16);z++){
                        cubes.add(new D6ChunkPos(x,y,z));if(cubes.size()>MAX_CUBES)throw new IllegalArgumentException("Compact mission volume cap27");
                    }
        }
        var access=WorldAccess.of(world);
        for(var cube:cubes)if(!access.isVolumeAvailable(cube))throw new IllegalArgumentException("Mission volume is not FULL/available");
    }
    private static EnclaveMissionContract current(EnclaveMissionLedger.Site site){
        var catalog=EnclaveMissionCatalog.snapshot();
        if(!catalog.valid())throw new IllegalArgumentException(catalog.problem());
        var definition=catalog.find(site.contract().id()).orElseThrow(()->new IllegalArgumentException("Mission definition unavailable"));
        if(!definition.hash().equals(site.contract().hash()))throw new IllegalArgumentException("Mission definition changed; original binding retained");
        return definition;
    }
    private static EnclaveMissionContract validate(ServerWorld world,ServerPlayerEntity player,EnclaveMissionLedger.Site site){
        if(!site.valid()||!site.context().equals(context(world)))throw new IllegalArgumentException("Mission site/world context changed");
        var definition=current(site);geometry(world,player,site.herald(),site.anchor(),site.steps());
        var state=WorldAccess.of(world).readIfLoaded(block(site.herald()));
        if(state.isEmpty()||!state.get().isOf(ModWorldBlocks.ENCLAVE_HERALD))throw new IllegalArgumentException("Original herald is missing or unavailable");
        var actual=EnclaveSite.generate(world.getSeed(),block(site.herald()));
        if(!actual.origin.id.equals(site.origin())||!EnclaveQuest.offer(actual).kind.name().equals(definition.offeredKind()))throw new IllegalArgumentException("Mission origin/offer changed");
        return definition;
    }
    private static void interactable(ServerWorld world,ServerPlayerEntity player,BlockPos herald,BlockHitResult hit){
        if(!survivor(world,player)||!herald.equals(hit.getBlockPos()))throw new IllegalArgumentException("Actual connected Survival owner required");
        double range=player.getBlockInteractionRange();var eye=player.getEyePos();var target=hit.getPos();
        double distance=target.distanceTo(eye);
        if(!Double.isFinite(range)||range<=0||!Double.isFinite(distance)||distance>range+.001)throw new IllegalArgumentException("Herald outside actual interaction reach");
        var end=target.add(target.subtract(eye).normalize().multiply(.002));
        if(!WorldEditSafety.loadedSegment(world,eye,end))throw new IllegalArgumentException("Herald ray volume unavailable");
        var ray=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        if(ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(herald))throw new IllegalArgumentException("Herald interaction is occluded");
    }
    /** Feedback never participates in a ledger/reputation transaction or retries it. */
    private static void notify(ServerPlayerEntity player,String message){
        try{player.sendMessage(Text.literal(message),false);}catch(RuntimeException lostFeedback){/* committed state remains authoritative */}
    }
    private static EnclaveMissionLedger.Site site(ServerWorld world,BlockPos herald){
        var ledger=FactionMemory.of(world).missions();
        if(!ledger.available())throw new IllegalArgumentException(ledger.problem().orElse("Mission state unavailable"));
        return ledger.site(world.getRegistryKey().getValue().toString(),point(herald))
            .orElseThrow(()->new IllegalArgumentException("No authored playable contract is bound here"));
    }
    static EnclaveMissionLedger.Site bind(ServerPlayerEntity player,String id,BlockPos herald,BlockPos anchor){
        if(!author(player))throw new IllegalArgumentException("Connected Creative operator permission2 required");
        var world=player.getServerWorld();var actual=context(world);var catalog=EnclaveMissionCatalog.snapshot();
        if(!catalog.valid())throw new IllegalArgumentException(catalog.problem());
        var definition=catalog.find(id).orElseThrow(()->new IllegalArgumentException("Unknown playable mission definition"));
        geometry(world,player,point(herald),point(anchor),definition.at(point(anchor)));
        var access=WorldAccess.of(world);var heraldState=access.readIfLoaded(herald);
        if(heraldState.isEmpty()||!heraldState.get().isOf(ModWorldBlocks.ENCLAVE_HERALD))throw new IllegalArgumentException("Existing loaded herald required");
        var origin=EnclaveSite.generate(world.getSeed(),herald);
        if(!origin.origin.id.equals(EnclaveOrigin.MINERS.id)||!EnclaveQuest.offer(origin).kind.name().equals(definition.offeredKind()))throw new IllegalArgumentException("This authored contract needs the existing MINERS/EXPLORE_SHAFT contact");
        for(var step:definition.at(point(anchor)))if(step.type()==Type.BREAK_BLOCK){
            var pos=block(step.block());var state=access.readIfLoaded(pos).orElseThrow(()->new IllegalArgumentException("Target is unavailable"));
            if(!Registries.BLOCK.getId(state.getBlock()).toString().equals(step.expectedBlock())||state.getBlock().getClass()!=Block.class
                ||!state.getEntries().isEmpty()||state.hasBlockEntity()||!state.getFluidState().isEmpty()||!state.isFullCube(world,pos))
                throw new IllegalArgumentException("Expected stable full-block panel must already exist; no generation or reactive targets");
        }
        var memory=FactionMemory.of(world);var ledger=memory.missions();long before=ledger.mutationVersion();
        try{return ledger.bind(actual,point(herald),point(anchor),origin.origin.id,definition);}
        finally{memory.missionChanged(before);}
    }
    static String inspect(ServerPlayerEntity player,BlockPos herald){
        var world=player.getServerWorld();if(!connected(world,player))throw new IllegalArgumentException("Actual connected player required");
        var site=site(world,herald);var definition=current(site);var ledger=FactionMemory.of(world).missions();
        return "Mission "+definition.id()+" at "+site.herald()+": "+ledger.status(player.getUuid(),site.id(),context(world),definition.hash())+". Sneak-use to accept or extract; acceptance grants no reward.";
    }
    static String accept(ServerPlayerEntity player,BlockPos herald,BlockHitResult hit){
        var world=player.getServerWorld();interactable(world,player,herald,hit);var site=site(world,herald);var definition=validate(world,player,site);
        var memory=FactionMemory.of(world);var ledger=memory.missions();long before=ledger.mutationVersion();
        final Outcome result;
        try{result=ledger.accept(player.getUuid(),site.id(),context(world),definition.hash(),position(player));}
        finally{memory.missionChanged(before);}
        return switch(result){
            case ACCEPTED->"Accepted "+definition.id()+". Complete the actual objectives and return here; no reputation granted.";
            case ACTIVE->"Mission active; complete its next real objective.";
            case READY->"Objectives complete; sneak-use this contact to extract.";
            case CLAIMED->"Already extracted; no additional reward.";
            case OWNER_BUSY->"Another contact owns your current mission.";
            default->"Mission unavailable: "+result;
        };
    }
    static String extract(ServerPlayerEntity player,BlockPos herald,BlockHitResult hit){
        var world=player.getServerWorld();interactable(world,player,herald,hit);var site=site(world,herald);var definition=validate(world,player,site);
        var memory=FactionMemory.of(world);
        if(memory.missions().status(player.getUuid(),site.id(),context(world),definition.hash())!=Outcome.READY)return "Extraction unavailable; objectives or ownership are incomplete.";
        return memory.claimMission(player.getUuid(),site.id(),context(world),definition.hash())
            ?"Extracted "+definition.id()+" (+3 "+site.origin()+").":"Extraction was not committed; no additional reward.";
    }
    public static ActionResult useHerald(ServerWorld world,BlockPos herald,ServerPlayerEntity player,BlockHitResult hit){
        String message;
        try{
            if(!connected(world,player))return ActionResult.CONSUME;
            if(!player.isSneaking())message=inspect(player,herald);
            else{
                interactable(world,player,herald,hit);var bound=site(world,herald);var definition=validate(world,player,bound);
                var status=FactionMemory.of(world).missions().status(player.getUuid(),bound.id(),context(world),definition.hash());
                message=status==Outcome.READY?extract(player,herald,hit):accept(player,herald,hit);
            }
        }catch(RuntimeException refused){message="Mission unavailable: "+Objects.toString(refused.getMessage(),refused.getClass().getSimpleName());}
        notify(player,message);return ActionResult.CONSUME;
    }
    public static void invalidateHerald(ServerWorld world,BlockPos herald){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Mission invalidation requires server thread");
        var memory=existing(world);if(memory==null)return;var ledger=memory.missions();long before=ledger.mutationVersion();
        ledger.invalidate(world.getRegistryKey().getValue().toString(),point(herald));memory.missionChanged(before);
    }
    /** Read an existing save only; passive ticks/breaks never create a state file. */
    private static FactionMemory existing(ServerWorld world){
        var overworld=world.getServer().getOverworld();
        return overworld==null?null:overworld.getPersistentStateManager().get(MEMORY_TYPE,FactionMemory.ID);
    }
    private static void pause(ServerWorld world,UUID owner){
        var memory=existing(world);if(memory!=null)memory.missions().pause(owner);
    }
    private static void tick(ServerWorld world){
        var memory=existing(world);if(memory==null||!memory.missions().available())return;var ledger=memory.missions();
        for(var owner:ledger.activeOwners()){
            var progress=ledger.active(owner).orElse(null);if(progress==null)continue;
            var bound=ledger.site(progress.site()).orElse(null);
            if(bound==null||!bound.context().dimension().equals(world.getRegistryKey().getValue().toString()))continue;
            var player=world.getServer().getPlayerManager().getPlayer(owner);
            if(player==null||!survivor(world,player)){ledger.pause(owner);continue;}
            long before=ledger.mutationVersion();
            String feedback=null;
            try{
                var definition=validate(world,player,bound);
                if(ledger.observePosition(owner,context(world),definition.hash(),position(player)))
                    feedback="Mission objective reached. "+ledger.active(owner).map(p->p.status()==Outcome.READY?"Return to the original contact to extract.":"Continue to the next objective.").orElse("");
            }catch(RuntimeException unavailable){ledger.pause(owner);}
            finally{memory.missionChanged(before);}
            if(feedback!=null)notify(player,feedback);
        }
    }
    private static void broke(ServerWorld world,ServerPlayerEntity player,BlockPos pos,BlockState previous,BlockEntity entity){
        if(!survivor(world,player)||entity!=null||previous.hasBlockEntity()||!WorldEditSafety.mayChange(world,pos,player))return;
        var memory=existing(world);if(memory==null||!memory.missions().available())return;var ledger=memory.missions();
        var progress=ledger.active(player.getUuid()).orElse(null);if(progress==null)return;
        var bound=ledger.site(progress.site()).orElse(null);if(bound==null)return;
        var current=WorldAccess.of(world).readIfLoaded(pos);if(current.isEmpty()||!current.get().isAir())return;
        var eye=player.getEyePos();double range=player.getBlockInteractionRange();
        var nearest=new Vec3d(MathHelper.clamp(eye.x,pos.getX(),pos.getX()+1.0),MathHelper.clamp(eye.y,pos.getY(),pos.getY()+1.0),MathHelper.clamp(eye.z,pos.getZ(),pos.getZ()+1.0));
        if(!Double.isFinite(range)||range<=0||eye.distanceTo(nearest)>range+.001||!WorldEditSafety.loadedSegment(world,eye,Vec3d.ofCenter(pos)))return;
        long before=ledger.mutationVersion();
        String feedback=null;
        try{
            var definition=validate(world,player,bound);
            if(ledger.observeBreak(player.getUuid(),context(world),definition.hash(),point(pos),Registries.BLOCK.getId(previous.getBlock()).toString(),position(player)))
                feedback="Actual mission panel removed. "+ledger.active(player.getUuid()).map(p->p.status()==Outcome.READY?"Return to the original contact to extract.":"Continue to the next objective.").orElse("");
        }catch(RuntimeException unavailable){ledger.pause(player.getUuid());}
        finally{memory.missionChanged(before);}
        if(feedback!=null)notify(player,feedback);
    }
    /** Authoring only: neither branch advances objectives or exposes a reward command. */
    private static void registerCommands(){
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->
            dispatcher.register(CommandManager.literal("ungravitymission")
                .requires(source->source.getEntity() instanceof ServerPlayerEntity player&&author(player))
                .then(CommandManager.literal("bind")
                    .then(CommandManager.argument("contract",IdentifierArgumentType.identifier())
                        .then(CommandManager.argument("herald",BlockPosArgumentType.blockPos())
                            .then(CommandManager.argument("anchor",BlockPosArgumentType.blockPos())
                                .executes(command->{
                                    var player=command.getSource().getPlayerOrThrow();
                                    try{
                                        var bound=bind(player,IdentifierArgumentType.getIdentifier(command,"contract").toString(),
                                            BlockPosArgumentType.getBlockPos(command,"herald"),BlockPosArgumentType.getBlockPos(command,"anchor"));
                                        notify(player,"Bound authored mission "+bound.contract().id()+" at "+bound.herald()+". No blocks, actors, progress or rewards were created.");
                                        return 1;
                                    }catch(RuntimeException refused){
                                        notify(player,"Mission binding refused: "+Objects.toString(refused.getMessage(),refused.getClass().getSimpleName()));return 0;
                                    }
                                })))))
                .then(CommandManager.literal("inspect")
                    .then(CommandManager.argument("herald",BlockPosArgumentType.blockPos())
                        .executes(command->{
                            var player=command.getSource().getPlayerOrThrow();
                            try{notify(player,inspect(player,BlockPosArgumentType.getBlockPos(command,"herald")));return 1;}
                            catch(RuntimeException refused){notify(player,"Mission unavailable: "+Objects.toString(refused.getMessage(),refused.getClass().getSimpleName()));return 0;}
                        })))));
    }
    public static synchronized void register(){
        if(registered)return;
        EnclaveMissionCatalog.register();
        ServerTickEvents.END_WORLD_TICK.register(EnclaveMissionSystem::tick);
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{
            if(world instanceof ServerWorld serverWorld&&player instanceof ServerPlayerEntity serverPlayer)broke(serverWorld,serverPlayer,pos,state,entity);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->pause(handler.player.getServerWorld(),handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,from,to)->pause(from,player.getUuid()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,damage)->{if(entity instanceof ServerPlayerEntity player)pause(player.getServerWorld(),player.getUuid());});
        registerCommands();registered=true;
    }
}
