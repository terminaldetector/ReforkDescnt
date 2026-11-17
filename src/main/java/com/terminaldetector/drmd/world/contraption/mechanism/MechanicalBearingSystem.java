package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.*;

/** Player-built mechanisms use material blocks and the existing persistent D6 joints.
 * No controller entity, duplicate rigid body, automatic turret blueprint or pose snapping. */
public final class MechanicalBearingSystem {
    public static final double MAX_SPEED=.6,MAX_TORQUE=40;
    public static final double MAX_LINEAR_SPEED=.6,MAX_LINEAR_FORCE=40,LINEAR_TRAVEL=2;
    private static final Vec3 X=new Vec3(1,0,0);
    private record Selection(RegistryKey<World> world,long tick,BlockPos corner,UUID body,BlockPos cell,long revision){}
    private static final Map<UUID,Selection> SELECTIONS=new HashMap<>();
    private MechanicalBearingSystem(){}
    public static void register(){
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SELECTIONS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SELECTIONS.clear());
    }
    private static boolean eligible(ServerPlayerEntity player){
        var mode=player.interactionManager.getGameMode();
        return player.getServer().isOnThread()&&player.isAlive()&&!player.hasVehicle()
            &&(mode==GameMode.CREATIVE||mode==GameMode.SURVIVAL)&&player.getMainHandStack().isOf(ModWorldBlocks.MECHANISM_WRENCH);
    }
    private static Selection pending(ServerPlayerEntity player){
        var selected=SELECTIONS.remove(player.getUuid());
        return selected!=null&&selected.world()==player.getWorld().getRegistryKey()
            &&player.getServer().getTicks()-selected.tick()>=0&&player.getServer().getTicks()-selected.tick()<=600?selected:null;
    }
    private static void message(ServerPlayerEntity player,String key){player.sendMessage(Text.translatable("message.drmd.mechanism."+key),true);}
    public static boolean usable(BlockState state){return state.getBlock() instanceof MechanicalBearingBlock;}
    private static boolean isDrive(BlockState state){return state.isOf(ModWorldBlocks.ROTARY_DRIVE)||state.isOf(ModWorldBlocks.LINEAR_DRIVE);}
    private static boolean bearing(BlockState state,BodyJointConstraint.Kind kind){
        return kind==BodyJointConstraint.Kind.MOTOR?state.isOf(ModWorldBlocks.MECHANICAL_BEARING)
            :kind==BodyJointConstraint.Kind.SLIDER&&state.isOf(ModWorldBlocks.SLIDING_BEARING);
    }
    private static Vec3 direction(BlockState state){var d=state.get(MechanicalBearingBlock.FACING);return new Vec3(d.getOffsetX(),d.getOffsetY(),d.getOffsetZ());}
    private static Vec3 point(BlockPos cell,BlockState state){return new Vec3(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5).plus(direction(state).scaled(.5));}
    private static Vec3d vanilla(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
    private static boolean editable(ServerPlayerEntity player,BlockBodyEntity body,BlockPos cell){
        var at=body.localToWorld(Vec3d.ofCenter(cell));
        return body.getWorld()==player.getWorld()&&!body.isRemoved()
            &&BodySpatialIndex.find(body.getWorld(),body.getId()).orElse(null)==body
            &&player.getEyePos().distanceTo(at)<=Math.min(16,player.getBlockInteractionRange())+1
            &&player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(at));
    }
    /** Called through the existing validated local-block packet; both clicks retain identity/revision. */
    public static boolean use(ServerPlayerEntity player,BlockBodyEntity body,BlockPos cell){
        if(!eligible(player)||!editable(player,body,cell))return false;
        var state=body.getLocalBlock(cell);if(!usable(state))return false;
        if(isDrive(state)){
            var updated=player.isSneaking()?state.cycle(Properties.INVERTED):state.with(Properties.POWER,((state.get(Properties.POWER)/5+1)%4)*5);
            if(!body.setLocalBlock(cell,updated))return false;
            refreshBody(body);message(player,"drive");return true;
        }
        var registry=BodyJointRegistry.get(player.getServerWorld());
        if(player.isSneaking()){
            SELECTIONS.remove(player.getUuid());
            for(var link:registry.forBody(body.getUuid()))if(link.blockDriven()&&owns(link,body,cell))registry.remove(link.id());
            message(player,"unlinked");return true;
        }
        var previous=pending(player);
        if(previous==null||previous.body()==null){
            SELECTIONS.put(player.getUuid(),new Selection(player.getWorld().getRegistryKey(),player.getServer().getTicks(),null,body.getUuid(),cell.toImmutable(),body.shapeRevision()));
            message(player,"bearing_selected");return true;
        }
        var first=BodySpatialIndex.loaded(player.getWorld()).stream().filter(b->b.getUuid().equals(previous.body())).findFirst().orElse(null);
        if(first==null||first.shapeRevision()!=previous.revision()||!editable(player,first,previous.cell())){message(player,"stale");return false;}
        if(!connect(player.getServerWorld(),first,previous.cell(),body,cell)){message(player,"alignment");return false;}
        message(player,"linked");return true;
    }
    private static boolean owns(BodyJointLink link,BlockBodyEntity body,BlockPos cell){return link.a().body().equals(body.getUuid())&&link.a().cell().equals(cell)||link.b().body().equals(body.getUuid())&&link.b().cell().equals(cell);}
    /** In-world two-corner assembly is bounded and validates every cell before the first mutation. */
    public static boolean useWorld(ServerPlayerEntity player,BlockPos corner){
        if(!eligible(player)||player.getEyePos().distanceTo(Vec3d.ofCenter(corner))>Math.min(16,player.getBlockInteractionRange())+1
            ||!player.getServerWorld().canPlayerModifyAt(player,corner))return false;
        var previous=pending(player);if(player.isSneaking()){message(player,"cancelled");return true;}
        if(previous==null||previous.corner()==null){
            SELECTIONS.put(player.getUuid(),new Selection(player.getWorld().getRegistryKey(),player.getServer().getTicks(),corner.toImmutable(),null,null,0));
            message(player,"corner_selected");return true;
        }
        var min=new BlockPos(Math.min(corner.getX(),previous.corner().getX()),Math.min(corner.getY(),previous.corner().getY()),Math.min(corner.getZ(),previous.corner().getZ()));
        var max=new BlockPos(Math.max(corner.getX(),previous.corner().getX()),Math.max(corner.getY(),previous.corner().getY()),Math.max(corner.getZ(),previous.corner().getZ()));
        if(max.getX()-min.getX()>=16||max.getY()-min.getY()>=16||max.getZ()-min.getZ()>=16){message(player,"selection_blocked");return false;}
        var world=player.getServerWorld();var access=WorldAccess.of(world);var cells=new LinkedHashMap<BlockPos,BlockState>();
        for(var cursor:BlockPos.iterate(min,max)){
            var snapshot=access.snapshotIfLoaded(cursor);
            if(snapshot.isEmpty()){message(player,"selection_blocked");return false;}
            var state=snapshot.get().state();if(state.isAir())continue;
            if(!world.canPlayerModifyAt(player,cursor)||!BlockBodyEntity.supports(state)||snapshot.get().entityData()!=null||state.getHardness(world,cursor)<0){message(player,"selection_blocked");return false;}
            cells.put(cursor.toImmutable(),state);
        }
        if(cells.isEmpty()){message(player,"selection_blocked");return false;}
        try{BlockBodyEntity.assembleSelection(world,cells);message(player,"assembled");return true;}
        catch(IllegalArgumentException|IllegalStateException invalid){message(player,"selection_blocked");return false;}
    }
    /** Public assembly primitive: material anchors must already meet; never move either body. */
    public static boolean connect(ServerWorld world,BlockBodyEntity a,BlockPos ca,BlockBodyEntity b,BlockPos cb){
        if(!world.getServer().isOnThread()||a==b||a.getWorld()!=world||b.getWorld()!=world||a.isRemoved()||b.isRemoved())return false;
        var sa=a.getLocalBlock(ca);var sb=b.getLocalBlock(cb);
        var kind=sa.isOf(ModWorldBlocks.SLIDING_BEARING)?BodyJointConstraint.Kind.SLIDER:BodyJointConstraint.Kind.MOTOR;
        if(!bearing(sa,kind)||!bearing(sb,kind))return false;
        var pa=point(ca,sa);var pb=point(cb,sb);var na=a.rotation().rotate(direction(sa));var nb=b.rotation().rotate(direction(sb));
        if(a.localToWorld(vanilla(pa)).distanceTo(b.localToWorld(vanilla(pb)))>.04||na.dot(nb)>-.999)return false;
        var registry=BodyJointRegistry.get(world);
        if(registry.forBody(a.getUuid()).stream().anyMatch(l->owns(l,a,ca))||registry.forBody(b.getUuid()).stream().anyMatch(l->owns(l,b,cb)))return false;
        var frameA=PortalTransform.rotationBetween(X,direction(sa));
        var frameB=ImmPtlQuaternions.hamiltonProduct(b.rotation().inverse(),ImmPtlQuaternions.hamiltonProduct(a.rotation(),frameA));
        var link=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(a.getUuid(),ca,pa,frameA),new BodyJointLink.Endpoint(b.getUuid(),cb,pb,frameB),kind,
            kind==BodyJointConstraint.Kind.SLIDER?0:Double.NEGATIVE_INFINITY,kind==BodyJointConstraint.Kind.SLIDER?LINEAR_TRAVEL:Double.POSITIVE_INFINITY,0,0,50000,50000,true,true);
        try{registry.add(world,a,b,link);}catch(IllegalArgumentException invalid){return false;}
        refresh(world,link,a,b);return true;
    }
    private static BlockState drive(BlockBodyEntity body,BodyJointLink.Endpoint endpoint,BodyJointConstraint.Kind kind){
        var support=body.getLocalBlock(endpoint.cell());
        if(!bearing(support,kind))return null;
        var state=body.getLocalBlock(endpoint.cell().offset(support.get(MechanicalBearingBlock.FACING).getOpposite()));
        var type=kind==BodyJointConstraint.Kind.SLIDER?ModWorldBlocks.LINEAR_DRIVE:ModWorldBlocks.ROTARY_DRIVE;
        return state.isOf(type)&&state.get(MechanicalBearingBlock.FACING)==support.get(MechanicalBearingBlock.FACING)?state:null;
    }
    /** Once per existing island solve, before integrating any forces. The registry owns all state.
     * Destroyed drives coast; destroyed/replaced bearing material breaks the attachment. */
    public static BodyJointLink refresh(ServerWorld world,BodyJointLink link,BlockBodyEntity a,BlockBodyEntity b){
        if(!link.blockDriven())return link;
        var registry=BodyJointRegistry.get(world);var sa=a.getLocalBlock(link.a().cell());var sb=b.getLocalBlock(link.b().cell());
        if(!bearing(sa,link.kind())||!bearing(sb,link.kind())
            ||direction(sa).dot(link.a().frame().rotate(X))<.999||direction(sb).dot(link.b().frame().rotate(X))>-.999){registry.remove(link.id());return null;}
        var da=drive(a,link.a(),link.kind());var db=drive(b,link.b(),link.kind());
        // Two sources on one axle are ambiguous: coast rather than grant extra torque.
        var source=da==null?db:db==null?da:null;
        double speed=source==null?0:source.get(Properties.POWER)*(link.kind()==BodyJointConstraint.Kind.SLIDER?MAX_LINEAR_SPEED:MAX_SPEED)/15*(source.get(Properties.INVERTED)?-1:1);
        double force=speed==0?0:link.kind()==BodyJointConstraint.Kind.SLIDER?MAX_LINEAR_FORCE:MAX_TORQUE;
        registry.configure(link.id(),speed,force,link.breakForce(),link.breakTorque());return registry.find(link.id()).orElse(null);
    }
    private static void refreshBody(BlockBodyEntity body){
        var world=(ServerWorld)body.getWorld();var loaded=new HashMap<UUID,BlockBodyEntity>();for(var b:BodySpatialIndex.loaded(world))loaded.put(b.getUuid(),b);
        for(var link:BodyJointRegistry.get(world).forBody(body.getUuid())){
            var a=loaded.get(link.a().body());var b=loaded.get(link.b().body());if(a!=null&&b!=null)refresh(world,link,a,b);
        }
    }
}
