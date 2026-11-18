package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.function.Function;

/** Optional ownership of continuous gun material. It neither schedules shots nor
 * reserves land, live players, targets or mechanical joints. */
public final class MountedDefenseClaims {
    public record Claim(UUID principal,UUID token,Direction facing){}
    private final Map<BlockPos,Claim> claims=new HashMap<>();
    private NbtElement opaque;
    public Optional<Claim> get(BlockPos cell){return Optional.ofNullable(claims.get(cell));}
    public int size(){return claims.size();}
    public boolean readOnly(){return opaque!=null;}
    private static boolean gun(BlockBodyEntity body,BlockPos cell){return body.getLocalBlock(cell).isOf(ModWorldBlocks.SHIP_LASER);}
    public boolean matches(BlockBodyEntity body,BlockPos cell,UUID principal,UUID token){
        var claim=claims.get(cell);return claim!=null&&claim.principal().equals(principal)&&claim.token().equals(token)
            &&gun(body,cell)&&MountedWeaponEmitter.facing(body,cell)==claim.facing();
    }
    /** Normal revisioned USE is the outer authority; repeat the live view and
     * permissions here so calling this adapter directly grants no remote claim. */
    public boolean toggle(ServerPlayerEntity player,BlockBodyEntity body,BlockPos cell){
        var mode=player.interactionManager.getGameMode();
        if(readOnly()||!player.getServer().isOnThread()||!player.isAlive()||player.hasVehicle()
            ||!(mode==GameMode.SURVIVAL||mode==GameMode.CREATIVE)||player.getWorld()!=body.getWorld()||body.isRemoved()
            ||!player.getMainHandStack().isOf(ModWorldBlocks.MECHANISM_WRENCH)||!gun(body,cell)
            ||BodySpatialIndex.find(body.getWorld(),body.getId()).orElse(null)!=body)return false;
        var target=BodyBlockInteraction.target(player,player.getEyePos(),BodyBlockInteraction.viewDirection(player),Math.min(16,player.getBlockInteractionRange()));
        if(target.isEmpty()||target.get().body()!=body||!target.get().hit().localBlock().equals(cell)
            ||!player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(body.localToWorld(Vec3d.ofCenter(cell)))))return false;
        var old=claims.get(cell);
        if(old!=null){if(!old.principal().equals(player.getUuid()))return false;claims.remove(cell);return true;}
        if(claims.size()>=BlockBodyEntity.MAX_BLOCKS)return false;
        claims.put(cell.toImmutable(),new Claim(player.getUuid(),UUID.randomUUID(),MountedWeaponEmitter.facing(body,cell)));return true;
    }
    /** Only a successful material commit may transfer ownership. A replacement
     * at the old coordinates is a new gun; unrelated edits do not revoke it. */
    public void materialChanged(BlockBodyEntity parent,List<BlockBodyEntity> fragments,List<BlockPos> changed,
                                Function<BlockBodyEntity,MountedDefenseClaims> state){
        if(opaque!=null){
            // The original unknown bytes stay only on their original owner.
            // Actual committed children inherit a fixed-size refusal, never
            // decoded authority or a deep copy of an unbounded unknown blob.
            if(!fragments.isEmpty()){
                var marker=new NbtCompound();marker.putInt("schema",-1);marker.putUuid("origin",refusalOrigin(parent.getUuid()));
                for(var fragment:fragments)state.apply(fragment).read(fragment,marker);
            }
            return;
        }
        for(var cell:changed){
            var claim=claims.remove(cell);if(claim==null)continue;
            if(!parent.getLocalBlock(cell).isAir())continue;
            for(var fragment:fragments)if(gun(fragment,cell)&&MountedWeaponEmitter.facing(fragment,cell)==claim.facing()){
                var next=state.apply(fragment);
                // Fresh fracture states inherit disjoint continuous material;
                // validated tokens were unique in the source. No per-cell scan
                // of the entire destination claim map during a large cut.
                if(!next.readOnly()&&next.claims.size()<BlockBodyEntity.MAX_BLOCKS&&!next.claims.containsKey(cell))next.claims.put(cell,claim);
                break;
            }
        }
    }
    /** Recognition affects only denial provenance, never live authorization.
     * Descendants keep one original UUID; malformed markers start a new bounded
     * refusal at this body while their original bytes remain untouched here. */
    private UUID refusalOrigin(UUID fallback){
        if(opaque instanceof NbtCompound n&&n.getKeys().size()==2&&n.contains("schema",NbtElement.INT_TYPE)&&n.getInt("schema")==-1
            &&n.containsUuid("origin")&&!n.getUuid("origin").equals(new UUID(0,0)))return n.getUuid("origin");
        return fallback;
    }
    public NbtElement write(){
        if(opaque!=null)return opaque.copy();if(claims.isEmpty())return null;
        var n=new NbtCompound();n.putInt("schema",1);var list=new NbtList();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var item=new NbtCompound();
            item.putLong("cell",entry.getKey().asLong());item.putUuid("principal",entry.getValue().principal());item.putUuid("token",entry.getValue().token());item.putString("facing",entry.getValue().facing().asString());list.add(item);});
        n.put("claims",list);return n;
    }
    /** Invalid/unsupported optional metadata is one opaque owned copy, not live
     * authority, and cannot interfere with the legacy cooldown codec. */
    public void read(BlockBodyEntity body,NbtElement input){
        claims.clear();opaque=null;if(input==null)return;
        opaque=input.copy();
        if(!(input instanceof NbtCompound n)||!n.contains("schema",NbtElement.INT_TYPE)||n.getInt("schema")!=1
            ||!(n.get("claims") instanceof NbtList list)||list.getHeldType()!=NbtElement.COMPOUND_TYPE&&!list.isEmpty()
            ||list.size()>BlockBodyEntity.MAX_BLOCKS)return;
        var parsed=new HashMap<BlockPos,Claim>();var tokens=new HashSet<UUID>();
        for(int i=0;i<list.size();i++){
            var item=list.getCompound(i);
            if(!item.contains("cell",NbtElement.LONG_TYPE)||!item.containsUuid("principal")||!item.containsUuid("token")||!item.contains("facing",NbtElement.STRING_TYPE))return;
            var cell=BlockPos.fromLong(item.getLong("cell"));var principal=item.getUuid("principal");var token=item.getUuid("token");var facing=Direction.byName(item.getString("facing"));
            if(!BlockBodyEntity.validLocal(cell)||!gun(body,cell)||facing==null||MountedWeaponEmitter.facing(body,cell)!=facing
                ||principal.equals(new UUID(0,0))||token.equals(new UUID(0,0))||!tokens.add(token)
                ||parsed.putIfAbsent(cell,new Claim(principal,token,facing))!=null)return;
        }
        claims.putAll(parsed);opaque=null;
    }
}
