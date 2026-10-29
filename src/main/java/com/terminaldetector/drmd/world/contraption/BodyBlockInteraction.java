package com.terminaldetector.drmd.world.contraption;

import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Server-authoritative mining/placement into a loaded moving local grid, never vanilla world edits. */
public final class BodyBlockInteraction {
    public static final int MINE=0,STOP=1,PLACE=2;
    public record Edit(UUID body,long revision,BlockPos local,int action) implements CustomPayload {
        public static final Id<Edit> ID=new Id<>(Identifier.of("drmd","body_edit"));
        public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.of((p,b)->{
            b.writeUuid(p.body);b.writeVarLong(p.revision);b.writeBlockPos(p.local);b.writeByte(p.action);
        },b->new Edit(b.readUuid(),b.readVarLong(),b.readBlockPos(),b.readUnsignedByte()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Target(BlockBodyEntity body,BlockBodyWorldAccess.BodyHit hit) {}
    public record BodyPose(com.terminaldetector.drmd.client.portal.PortalTransform.Vec3 position,com.terminaldetector.drmd.client.portal.PortalTransform.Quat rotation) {}
    private static final class Mining {
        final Edit edit;final ItemStack tool;final net.minecraft.registry.RegistryKey<World> dimension;
        long lastPulse,lastAdvanced=Long.MIN_VALUE;double progress;
        Mining(ServerPlayerEntity p,Edit edit,long now){this.edit=edit;tool=p.getMainHandStack().copy();dimension=p.getWorld().getRegistryKey();lastPulse=now;}
    }
    private record Budget(long tick,int count) {}
    private static final Map<UUID,Budget> BUDGET=new HashMap<>();
    private static final Map<UUID,Mining> MINING=new HashMap<>();
    private static final Map<UUID,Long> LAST_ACTION=new HashMap<>();
    private BodyBlockInteraction() {}
    private static boolean creative(ServerPlayerEntity p){return p.interactionManager.getGameMode()==GameMode.CREATIVE;}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(e,c)->c.server().execute(()->receive(c.player(),e)));
        ServerTickEvents.END_WORLD_TICK.register(w->{for(var p:w.getPlayers())tick(p);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{MINING.remove(h.player.getUuid());LAST_ACTION.remove(h.player.getUuid());BUDGET.remove(h.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{MINING.clear();LAST_ACTION.clear();BUDGET.clear();});
    }
    public static Optional<Target> target(PlayerEntity p,Vec3d from,Vec3d direction,double reach) {
        return target(p,from,direction,reach,b->new BodyPose(b.massPosition(),b.rotation()));
    }
    public static Optional<Target> target(PlayerEntity p,Vec3d from,Vec3d direction,double reach,java.util.function.Function<BlockBodyEntity,BodyPose> pose) {
        if(!Double.isFinite(from.lengthSquared())||!Double.isFinite(direction.lengthSquared())||!Double.isFinite(reach)||reach<=0||reach>64||direction.lengthSquared()<1e-8)return Optional.empty();
        Vec3d to=from.add(direction.normalize().multiply(reach));
        // Shape providers may read one neighbour beyond the ray. Never generate/join an unknown volume.
        var region=new Box(from,to).expand(1);var access=com.terminaldetector.drmd.world.access.WorldAccess.of(p.getWorld());
        for(int x=MathHelper.floor(region.minX)>>4;x<=MathHelper.floor(region.maxX)>>4;x++)
        for(int y=MathHelper.floor(region.minY)>>4;y<=MathHelper.floor(region.maxY)>>4;y++)
        for(int z=MathHelper.floor(region.minZ)>>4;z<=MathHelper.floor(region.maxZ)>>4;z++) {
            var cube=new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z);
            if(com.terminaldetector.drmd.world.access.CubicAvailability.withinHeight(p.getWorld(),cube)&&!access.isVolumeAvailable(cube))return Optional.empty();
        }
        var terrain=p.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        double limit=terrain.getType()==HitResult.Type.MISS?1:from.distanceTo(terrain.getPos())/reach;
        Target best=null;
        for(var b:BodySpatialIndex.query(p.getWorld(),new Box(from,to).expand(1))) {
            var frame=pose.apply(b);var hit=new BlockBodyWorldAccess(b).raycastBlockBody(from,to,frame.position(),frame.rotation());
            if(hit.isPresent()&&!hit.get().inside()&&hit.get().fraction()<limit-1e-7) {limit=hit.get().fraction();best=new Target(b,hit.get());}
        }
        if(best!=null)for(var e:p.getWorld().getOtherEntities(p,new Box(from,to).expand(1),e->e.canHit()&&e.isAlive()&&!e.isSpectator()&&!(e instanceof BlockBodyEntity))) {
            var hit=e instanceof com.terminaldetector.drmd.physics.PhysicsTarget t?t.physicsRaycast(from,to):e.getBoundingBox().expand(e.getTargetingMargin()).raycast(from,to);
            if(hit.isPresent()&&from.distanceTo(hit.get())<limit*reach-1e-6)return Optional.empty();
        }
        return Optional.ofNullable(best);
    }
    private static Optional<Target> validated(ServerPlayerEntity p,Edit e) {
        if(p.isSpectator()||!p.isAlive()||p.hasVehicle()||!(creative(p)||p.interactionManager.getGameMode()==GameMode.SURVIVAL))return Optional.empty();
        var target=target(p,p.getEyePos(),p.getRotationVec(1),Math.min(16,p.getBlockInteractionRange()));
        if(target.isEmpty())return Optional.empty();var t=target.get();
        if(!t.body().getUuid().equals(e.body)||t.hit().revision()!=e.revision||!t.hit().localBlock().equals(e.local)
            ||!p.getServerWorld().canPlayerModifyAt(p,BlockPos.ofFloored(t.hit().worldPoint().x(),t.hit().worldPoint().y(),t.hit().worldPoint().z())))return Optional.empty();
        return target;
    }
    public static boolean receive(ServerPlayerEntity p,Edit e) {
        if(!p.getServer().isOnThread()||e.action<0||e.action>2)return false;
        if(e.action==STOP){MINING.remove(p.getUuid());return true;}
        long now=p.getServer().getTicks();var budget=BUDGET.get(p.getUuid());
        int count=budget!=null&&budget.tick()==now?budget.count():0;if(count>=4)return false;
        BUDGET.put(p.getUuid(),new Budget(now,count+1));
        var target=validated(p,e);if(target.isEmpty())return false;
        if(e.action==MINE&&!creative(p)) {
            var old=MINING.get(p.getUuid());
            if(old==null||!old.edit.equals(e)||!ItemStack.areItemsAndComponentsEqual(old.tool,p.getMainHandStack()))MINING.put(p.getUuid(),new Mining(p,e,now));
            else old.lastPulse=now;return true;
        }
        if(now-LAST_ACTION.getOrDefault(p.getUuid(),Long.MIN_VALUE/2)<4)return false;
        boolean edited=e.action==MINE?breakBlock(p,target.get()):place(p,target.get());
        if(edited){LAST_ACTION.put(p.getUuid(),now);MINING.remove(p.getUuid());}return edited;
    }
    public static void tick(ServerPlayerEntity p) {
        var mine=MINING.get(p.getUuid());if(mine==null)return;
        long now=p.getServer().getTicks();if(mine.lastAdvanced==now)return;mine.lastAdvanced=now;
        if(p.getServer().getTicks()-mine.lastPulse>4||p.getWorld().getRegistryKey()!=mine.dimension
            ||!ItemStack.areItemsAndComponentsEqual(mine.tool,p.getMainHandStack())){MINING.remove(p.getUuid());return;}
        var target=validated(p,mine.edit);if(target.isEmpty()){MINING.remove(p.getUuid());return;}
        var state=target.get().body().getLocalBlock(mine.edit.local);float hardness=state.getHardness(p.getWorld(),BlockPos.ofFloored(p.getPos()));
        if(hardness<0){MINING.remove(p.getUuid());return;}
        mine.progress+=hardness==0?1:p.getBlockBreakingSpeed(state)/hardness/(p.canHarvest(state)?30:100);
        if(mine.progress>=1){breakBlock(p,target.get());MINING.remove(p.getUuid());}
    }
    private static boolean breakBlock(ServerPlayerEntity p,Target t) {
        var state=t.body().getLocalBlock(t.hit().localBlock());var tool=p.getMainHandStack();
        var at=new Vec3d(t.hit().worldPoint().x(),t.hit().worldPoint().y(),t.hit().worldPoint().z());
        var drops=!creative(p)&&p.canHarvest(state)&&p.getWorld().getGameRules().getBoolean(GameRules.DO_TILE_DROPS)
            ?Block.getDroppedStacks(state,p.getServerWorld(),BlockPos.ofFloored(at),null,p,tool):List.<ItemStack>of();
        Vec3d velocity=t.body().surfaceVelocityAt(at).multiply(.05);
        if(!new BlockBodyWorldAccess(t.body()).breakLocalBlock(t.hit().localBlock()))return false;
        if(!creative(p)&&state.getHardness(p.getWorld(),BlockPos.ofFloored(at))>0&&tool.isDamageable())tool.damage(1,p,EquipmentSlot.MAINHAND);
        for(var stack:drops)if(!stack.isEmpty()) {var item=new ItemEntity(p.getWorld(),at.x,at.y,at.z,stack);item.setVelocity(velocity);item.setToDefaultPickupDelay();p.getWorld().spawnEntity(item);}
        return true;
    }
    private static boolean place(ServerPlayerEntity p,Target t) {
        var stack=p.getMainHandStack();if(!(stack.getItem() instanceof BlockItem item)||stack.isEmpty())return false;
        var state=item.getBlock().getDefaultState();
        // Orient existing vanilla pillar states in the local grid; world UP cannot orient a ship log.
        if(state.contains(net.minecraft.state.property.Properties.AXIS))state=state.with(net.minecraft.state.property.Properties.AXIS,t.hit().localFace().getAxis());
        if(!new BlockBodyWorldAccess(t.body()).placeLocalBlock(t.hit().localBlock().offset(t.hit().localFace()),state))return false;
        if(!creative(p)){stack.decrement(1);p.getInventory().markDirty();}return true;
    }
}
