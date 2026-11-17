package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.enclave.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.block.*;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.*;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Test-owned actors and arena. Mission state is observed only through read-only NBT snapshots. */
final class EnclaveMissionGameplayFixture {
    enum Case { LAB,SIDE,FOREIGN,RIGHTS,INVALIDATE }
    record Actor(ServerPlayerEntity player,EmbeddedChannel channel,OperatorEntry operatorBefore){}
    final TestContext c;final ServerWorld w;final Case scenario;final BlockPos herald,anchor,otherHerald,panel;
    final String definition;final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
    final List<Actor> actors=new ArrayList<>();final List<Map<String,Object>> trace=new ArrayList<>();
    final Set<UUID> preexistingItems=new HashSet<>(),ownedDrops=new HashSet<>();
    AiFixtureChunks chunks;Actor author,owner,foreign;FactionMemory memory;UUID siteId;
    int admission,phase,phaseTicks,miningTicks,sequence,commandCount,useCount,returnLeg;long lastTick=Long.MIN_VALUE;
    boolean finished,closed,miningStarted;Actor miner;String repeatReceipt;

    EnclaveMissionGameplayFixture(TestContext context,Case scenario){
        c=context;w=c.getWorld();this.scenario=scenario;definition=scenario==Case.SIDE?"drmd:lab_side_entry":"drmd:lab_infiltration";
        var base=c.getAbsolutePos(new BlockPos(3,2,3));BlockPos chosen=null;
        int cx=Math.floorDiv(base.getX(),EnclaveSite.CELL),cz=Math.floorDiv(base.getZ(),EnclaveSite.CELL);
        for(int dx=-5;dx<=5&&chosen==null;dx++)for(int dz=-5;dz<=5;dz++){
            // Independent reserved sites even when separate GameTest structures share a384 cell.
            var candidate=new BlockPos((cx+dx)*EnclaveSite.CELL+40+scenario.ordinal()*48,base.getY(),(cz+dz)*EnclaveSite.CELL+40);
            if(EnclaveSite.generate(w.getSeed(),candidate).origin==EnclaveOrigin.MINERS){chosen=candidate;break;}
        }
        c.assertTrue(chosen!=null,"bounded121-cell metadata search finds a compatible actual MINERS contact");
        herald=chosen;anchor=herald.add(10,0,0);otherHerald=herald.add(0,0,4);
        panel=anchor.add(scenario==Case.SIDE?4:1,0,scenario==Case.SIDE?4:0);
    }
    void tick(){
        w.resetIdleTimeout();
        c.assertTrue(c.getTick()<480,"500 framework retains bounded cleanup margin; scenario="+scenario+" phase="+phase+" age="+phaseTicks);
        if(lastTick!=Long.MIN_VALUE)c.assertTrue(c.getTick()==lastTick+1,"one registered driver observes consecutive actual game ticks");lastTick=c.getTick();
        if(chunks==null){chunks=new AiFixtureChunks(w,herald.add(7,0,2),1);return;}
        if(!chunks.entityTicking(herald,anchor,panel)){
            c.assertTrue(++admission<80,"actual entity-ticking fixture admission bounded80: "+chunks.readiness(herald,anchor,panel));return;
        }
        if(owner==null){admit();return;}
        phaseTicks++;step();
    }
    private void put(BlockPos pos,BlockState state){
        var old=WorldAccess.of(w).readIfLoaded(pos).orElseThrow(()->new IllegalStateException("fixture block not loaded "+pos));
        c.assertTrue(!old.hasBlockEntity(),"fixture never replaces pre-existing block entities");
        previous.putIfAbsent(pos.toImmutable(),old);c.assertTrue(previous.size()<=1024,"fixture touches at most1024 owned cells");
        w.setBlockState(pos,state,Block.NOTIFY_ALL);
    }
    private Actor connect(GameMode mode,boolean op){
        UUID id=UUID.randomUUID();var profile=new GameProfile(id,"mq-"+id.toString().substring(0,8));
        var data=ConnectedClientData.createDefault(profile,false);var player=new ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());
        var connection=new ClientConnection(NetworkSide.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        var before=w.getServer().getPlayerManager().getOpList().get(profile);var actor=new Actor(player,channel,before);actors.add(actor);
        w.getServer().getPlayerManager().onPlayerConnect(connection,player,data);
        // Actual TestServer#getOpPermissionLevel is0; use the ordinary authority
        // list's explicit level2 entry, not addToOperators' global default0.
        if(op){
            w.getServer().getPlayerManager().getOpList().add(new OperatorEntry(profile,2,false));
            w.getServer().getPlayerManager().sendCommandTree(player);
        }
        player.changeGameMode(mode);DescentPlayerData.get(player).setEnabled(false);player.getInventory().clear();
        player.setNoGravity(true);player.getAbilities().flying=false;player.setVelocity(Vec3d.ZERO);
        player.setPosition(herald.getX()+.5,herald.getY(),herald.getZ()-2+.5);player.setSneaking(false);
        var entry=w.getServer().getPlayerManager().getOpList().get(profile);
        System.out.println("ENCLAVE_GAMEPLAY_ACTUAL_AUTHORITY="+new com.google.gson.Gson().toJson(Map.of(
            "owner",id.toString(),"mode",player.interactionManager.getGameMode().toString(),"author",op,
            "previousEntryLevel",before==null?-1:before.getPermissionLevel(),"actualEntryLevel",entry==null?-1:entry.getPermissionLevel(),
            "actualPermission2",player.hasPermissionLevel(2),"actualManagerIdentity",w.getServer().getPlayerManager().getPlayer(id)==player)));
        c.assertTrue(w.getServer().getPlayerManager().getPlayer(id)==player&&w.getEntity(id)==player,"actual connected player identity is in manager and world");
        if(op)c.assertTrue(player.hasPermissionLevel(2),"actual Creative author has real operator permission2");
        else c.assertTrue(!player.hasPermissionLevel(2)&&entry==null,"ordinary Survival actors retain no operator authority");
        return actor;
    }
    private void admit(){
        c.assertTrue(EnclaveMissionCatalog.snapshot().valid()&&EnclaveMissionCatalog.find(definition).isPresent(),"production main registered actual server-data mission resource before gameplay");
        for(var item:items())preexistingItems.add(item.getUuid());
        for(int x=-2;x<=16;x++)for(int z=-3;z<=7;z++){
            put(herald.add(x,-1,z),Blocks.STONE.getDefaultState());
            for(int y=0;y<3;y++)put(herald.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        put(herald,ModWorldBlocks.ENCLAVE_HERALD.getDefaultState());put(otherHerald,ModWorldBlocks.ENCLAVE_HERALD.getDefaultState());put(panel,Blocks.IRON_BLOCK.getDefaultState());
        author=connect(GameMode.CREATIVE,true);owner=connect(GameMode.SURVIVAL,false);foreign=connect(GameMode.SURVIVAL,false);
        author.player.setPosition(herald.getX()-1+.5,herald.getY(),herald.getZ()+6+.5);
        foreign.player.setPosition(herald.getX()+2+.5,herald.getY(),herald.getZ()+6+.5);
        memory=FactionMemory.of(w);
        for(var actor:actors)c.assertTrue(rep(actor)==0,"fresh actor has no inherited miners reputation");
        c.assertTrue(WorldEditSafety.mayChange(w,herald,owner.player)&&WorldEditSafety.mayChange(w,panel,owner.player),"actual unprivileged Survival owner has bounds/FULL/spawn permissions");
        record("admitted");
    }
    private NbtCompound snapshot(){return memory.writeNbt(new NbtCompound(),w.getRegistryManager());}
    private NbtCompound site(){
        var sites=snapshot().getCompound("missions").getList("sites",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<sites.size();i++){
            var s=sites.getCompound(i);var h=s.getCompound("herald");
            if(h.getInt("x")==herald.getX()&&h.getInt("y")==herald.getY()&&h.getInt("z")==herald.getZ())return s;
        }
        return new NbtCompound();
    }
    private NbtCompound instance(Actor actor){
        var list=snapshot().getCompound("missions").getList("instances",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            var tag=list.getCompound(i);
            if(tag.getUuid("owner").equals(actor.player.getUuid())&&siteId!=null&&tag.getUuid("site").equals(siteId))return tag;
        }
        return new NbtCompound();
    }
    private int cursor(Actor actor){var tag=instance(actor);return tag.isEmpty()?-1:tag.getInt("cursor");}
    private String status(Actor actor){
        var tag=instance(actor);if(tag.isEmpty())return "NONE";String raw=tag.getString("status");
        return raw.equals("ACTIVE")&&tag.getInt("cursor")==EnclaveMissionCatalog.find(definition).orElseThrow().objectives().size()?"READY":raw;
    }
    private int rep(Actor actor){return memory.getRep(actor.player.getUuid(),EnclaveOrigin.MINERS);}
    private void expect(Actor actor,int cursor,String state,int rep,String why){
        c.assertTrue(cursor(actor)==cursor&&status(actor).equals(state)&&rep(actor)==rep,
            why+" actual cursor="+cursor(actor)+" status="+status(actor)+" rep="+rep(actor)+" instance="+instance(actor));
    }
    private static String xyz(BlockPos pos){return pos.getX()+" "+pos.getY()+" "+pos.getZ();}
    private void command(ServerCommandSource source,String text){
        commandCount++;w.getServer().getCommandManager().executeWithPrefix(source,text);
        record("dispatcher:"+text);
    }
    private String bindCommand(){return "ungravitymission bind "+definition+" "+xyz(herald)+" "+xyz(anchor);}
    private void bind(){
        var before=snapshot();String blocks=fingerprint();int actors=w.getOtherEntities(null,arenaBox()).size();
        command(author.player.getCommandSource(),bindCommand());
        var bound=site();c.assertTrue(!bound.isEmpty(),"actual connected Creative OP2 dispatcher binds the authored resource");siteId=bound.getUuid("id");
        c.assertTrue(bound.getBoolean("valid")&&bound.getString("origin").equals("miners")&&bound.getString("hash").equals(EnclaveMissionCatalog.find(definition).orElseThrow().hash()),"bound site records real origin and current resource hash");
        var context=bound.getCompound("context");c.assertTrue(context.getString("dimension").equals(w.getRegistryKey().getValue().toString())&&context.getLong("seed")==w.getSeed()&&context.getInt("bottom")==w.getBottomY()&&context.getInt("top")==w.getTopY(),"actual bound world/seed/signed Y bounds are retained");
        c.assertTrue(fingerprint().equals(blocks)&&w.getOtherEntities(null,arenaBox()).size()==actors,"binding creates no blocks or actors");
        c.assertTrue(snapshot().getCompound("missions").getList("instances",NbtElement.COMPOUND_TYPE).equals(before.getCompound("missions").getList("instances",NbtElement.COMPOUND_TYPE)),"binding cannot create a mission instance or progress");
        for(var actor:this.actors)c.assertTrue(rep(actor)==0,"binding awards nobody reputation");
        var root=w.getServer().getCommandManager().getDispatcher().getRoot().getChild("ungravitymission");
        c.assertTrue(root!=null&&root.getChildren().stream().map(node->node.getName()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("bind","inspect")),"production dispatcher exposes author bind/inspect only, no raw progress or reward command");
        String unchanged=snapshot().toString();command(author.player.getCommandSource(),"ungravitymission inspect "+xyz(herald));
        c.assertTrue(snapshot().toString().equals(unchanged),"real authorized inspect command cannot change progress or reward");
    }
    private Box arenaBox(){return new Box(herald.getX()-2,herald.getY()-1,herald.getZ()-3,herald.getX()+17,herald.getY()+3,herald.getZ()+8);}
    private String fingerprint(){
        var value=new StringBuilder();
        for(var pos:previous.keySet())value.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append('=').append(WorldAccess.of(w).readIfLoaded(pos).orElseThrow()).append(';');
        return Integer.toHexString(value.toString().hashCode())+":"+value.length();
    }
    private Vec3d standing(BlockPos feet){return new Vec3d(feet.getX()+.5,feet.getY(),feet.getZ()+.5);}
    private BlockPos home(){return herald.add(0,0,-2);}
    private BlockPos firstEntry(){return scenario==Case.SIDE?anchor.add(1,0,1):anchor.add(0,0,-1);}
    private BlockPos miningStance(){return panel.add(0,0,-2);}
    private boolean walk(Actor actor,BlockPos destination){
        var p=actor.player;var goal=standing(destination);var delta=goal.subtract(p.getPos());
        p.setSneaking(false);p.setVelocity(Vec3d.ZERO);double horizontal=Math.hypot(delta.x,delta.z);
        double scale=horizontal>.30?.30/horizontal:1;
        p.move(MovementType.SELF,new Vec3d(delta.x*scale,-.08,delta.z*scale));
        c.assertTrue(Math.abs(p.getY()-destination.getY())<.01,"normal walking remains on actual fixture floor, no teleport/travel substitute");
        return Math.hypot(p.getX()-goal.x,p.getZ()-goal.z)<.025;
    }
    private void aim(Actor actor,Vec3d target){
        var p=actor.player;var delta=target.subtract(p.getEyePos());
        p.setYaw((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
        p.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));p.setHeadYaw(p.getYaw());
    }
    private void use(Actor actor,BlockPos contact,boolean sneak){
        var p=actor.player;p.setSneaking(sneak);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
        Vec3d target=Vec3d.ofCenter(contact).add(0,0,-.5);aim(actor,target);
        c.assertTrue(w.getServer().getPlayerManager().getPlayer(p.getUuid())==p&&w.getEntity(p.getUuid())==p&&p.isAlive(),"every use has actual connected alive owner identity");
        c.assertTrue(p.getEyePos().distanceTo(target)<=p.getBlockInteractionRange(),"ordinary Herald use remains within actual reach");
        var ray=w.raycast(new net.minecraft.world.RaycastContext(p.getEyePos(),target.add(0,0,.002),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,p));
        c.assertTrue(ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(contact),"ordinary Herald ray hits the intended live contact first");
        var result=p.interactionManager.interactBlock(p,w,p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(target,Direction.NORTH,contact,false));
        useCount++;record("actual-use:"+p.getUuid()+":"+sneak+":"+result);
        if(p.interactionManager.getGameMode()!=GameMode.SPECTATOR)c.assertTrue(result.isAccepted(),"ordinary live Herald block interaction is accepted");
    }
    private boolean mine(Actor actor){
        if(!miningStarted){
            if(!walk(actor,miningStance()))return false;
            var p=actor.player;p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));p.setSneaking(false);
            var target=Vec3d.ofCenter(panel).add(0,0,-.5);aim(actor,target);
            c.assertTrue(w.getBlockState(panel).isOf(Blocks.IRON_BLOCK)&&p.canHarvest(w.getBlockState(panel))&&p.isOnGround(),"real Survival diamond pickaxe begins harvestable iron-panel mining on actual floor");
            c.assertTrue(WorldEditSafety.mayChange(w,panel,p)&&WorldEditSafety.loadedSegment(w,p.getEyePos(),target),"actual normal mining has permissions and loaded reach");
            var hit=w.raycast(new net.minecraft.world.RaycastContext(p.getEyePos(),target.add(0,0,.002),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,p));
            c.assertTrue(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(panel),"real panel is first collider on ordinary mining ray");
            miner=actor;miningStarted=true;miningTicks=0;
            p.interactionManager.processBlockBreakingAction(panel,PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,Direction.NORTH,w.getTopY(),++sequence);record("mining-start");return false;
        }
        c.assertTrue(actor==miner,"one actual miner owns this fixture's normal mining sequence");
        miningTicks++;c.assertTrue(miningTicks<=200,"normal iron-panel mining bounded200 actual ticks; no direct break shortcut");
        if(w.getBlockState(panel).isAir()){
            c.assertTrue(miningTicks>1,"actual panel removal follows normal updates rather than instant creative/setup destruction");
            for(var drop:items())if(!preexistingItems.contains(drop.getUuid()))ownedDrops.add(drop.getUuid());
            record("mining-real-air");return true;
        }
        // One normal stop request after20 actual updates; the real manager completes it or
        // continues its failed-to-mine recovery. No forced tryBreakBlock/event/setBlock call.
        if(miningTicks==20)actor.player.interactionManager.processBlockBreakingAction(panel,PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,Direction.NORTH,w.getTopY(),++sequence);
        return false;
    }
    private void next(int phase){this.phase=phase;phaseTicks=0;record("phase:"+phase);}
    private void step(){
        switch(phase){
            case 0->{if(scenario==Case.RIGHTS)deniedAuthoring();bind();next(1);}
            case 1->{use(owner,herald,true);expect(owner,0,"ACTIVE",0,"actual acceptance grants no objective or reward");next(2);}
            case 2->{use(owner,herald,true);expect(owner,0,"ACTIVE",0,"repeat acceptance is idempotent");next(3);}
            case 3->{use(owner,herald,false);expect(owner,0,"ACTIVE",0,"ordinary Survival status inspection is inert");next(4);}
            case 4->{use(owner,herald,true);expect(owner,0,"ACTIVE",0,"early return cannot extract an incomplete mission");next(5);}
            case 5->{
                if(scenario!=Case.RIGHTS){next(6);break;}
                if(phaseTicks==1){owner.player.changeGameMode(GameMode.CREATIVE);use(owner,herald,true);expect(owner,0,"ACTIVE",0,"Creative acceptance does not progress or reward");}
                else if(phaseTicks==2){owner.player.changeGameMode(GameMode.SPECTATOR);use(owner,herald,true);expect(owner,0,"ACTIVE",0,"Spectator acceptance does not progress or reward");}
                else{owner.player.changeGameMode(GameMode.SURVIVAL);next(6);}
            }
            case 6->{if(walk(owner,firstEntry()))next(7);}
            case 7->{
                if(!miningStarted)expect(owner,1,"ACTIVE",0,"only actual owner outside-to-inside tick admits panel objective");
                if(mine(scenario==Case.FOREIGN?foreign:owner))next(8);
            }
            case 8->{
                if(scenario==Case.FOREIGN){expect(owner,1,"ACTIVE",0,"actual foreign AFTER-break does not advance owner despite AIR");next(9);}
                else if(scenario==Case.SIDE){expect(owner,2,"ACTIVE",0,"second JSON requires final region after actual mining");next(9);}
                else{expect(owner,2,"READY",0,"real entry and successful mining only prepare extraction");next(13);}
            }
            case 9->{if(walk(owner,home()))next(10);}
            case 10->{
                if(scenario==Case.FOREIGN){
                    use(owner,herald,phaseTicks%2==1);expect(owner,1,"ACTIVE",0,"absent foreign-mined panel is never inferred complete");
                    c.assertTrue(w.getBlockState(panel).isAir()&&rep(foreign)==0&&instance(foreign).isEmpty(),"foreign miner has neither mission nor reward and missing panel remains missing");
                    if(phaseTicks==4)next(16);
                }else{use(owner,herald,true);expect(owner,2,"ACTIVE",0,"return before final data-defined entry cannot extract");next(11);}
            }
            case 11->{if(walk(owner,anchor.add(-3,0,2)))next(12);}
            case 12->{expect(owner,3,"READY",0,"distinct actual final entry completes the second data-only route");next(13);}
            case 13->{if(walk(owner,scenario==Case.INVALIDATE?herald.add(0,0,2):home()))next(14);}
            case 14->{
                if(scenario==Case.INVALIDATE){
                    use(owner,otherHerald,true);expect(owner,2,"READY",0,"another unbound contact cannot extract this site's mission");
                    put(herald,Blocks.AIR.getDefaultState());put(herald,ModWorldBlocks.ENCLAVE_HERALD.getDefaultState());
                    c.assertTrue(!site().getBoolean("valid"),"actual Herald replacement invalidates original reserved binding");
                    expect(owner,2,"BLOCKED",0,"replacement at identical signed XYZ cannot inherit ready progress");next(15);
                }else if(scenario==Case.RIGHTS){rightsExtraction();}
                else{use(owner,herald,true);claimed();next(15);}
            }
            case 15->{
                if(scenario==Case.INVALIDATE){
                    // The restored solid Herald lies between south contact and north home.
                    // Walk normally around it; retain collision, height and the same deadline.
                    if(returnLeg<2){
                        if(walk(owner,herald.add(-1,0,returnLeg==0?2:-2))){returnLeg++;record("invalidation-return-waypoint:"+returnLeg);}
                        break;
                    }
                    if(!walk(owner,home()))break;
                    use(owner,herald,true);expect(owner,2,"BLOCKED",0,"new live block cannot extract invalidated ready claim");
                    var reserved=site().getUuid("id");
                    // Inactive authored panel is rebuilt ONLY to reach rebind admission, not to
                    // fabricate an owner objective; the original instance must stay BLOCKED.
                    put(panel,Blocks.IRON_BLOCK.getDefaultState());command(author.player.getCommandSource(),bindCommand());
                    c.assertTrue(site().getUuid("id").equals(reserved)&&!site().getBoolean("valid"),"actual rebind cannot overwrite reserved invalidated identity");
                    expect(owner,2,"BLOCKED",0,"reserved rebind refusal cannot reset objective/claim state");next(16);
                }else{
                    use(owner,herald,phaseTicks<=3);claimed();
                    c.assertTrue(instance(owner).toString().equals(repeatReceipt),"repeat/status uses retain the exact existing claim receipt");
                    if(phaseTicks==4)next(16);
                }
            }
            case 16->{verifyPackets();record("complete");finished=true;close(null);c.complete();}
            default->throw new IllegalStateException("unknown fixture phase "+phase);
        }
    }
    private void deniedAuthoring(){
        String before=snapshot().toString(),blocks=fingerprint();
        command(owner.player.getCommandSource(),bindCommand());
        command(owner.player.getCommandSource(),"ungravitymission inspect "+xyz(herald));
        command(foreign.player.getCommandSource(),bindCommand());
        foreign.player.changeGameMode(GameMode.CREATIVE);command(foreign.player.getCommandSource(),bindCommand());
        foreign.player.changeGameMode(GameMode.SPECTATOR);command(foreign.player.getCommandSource(),bindCommand());
        foreign.player.changeGameMode(GameMode.SURVIVAL);
        command(w.getServer().getCommandSource(),bindCommand());
        var profile=new GameProfile(UUID.randomUUID(),"mq-unconnected");var data=ConnectedClientData.createDefault(profile,false);
        var unconnected=new ServerPlayerEntity(w.getServer(),w,profile,data.syncedOptions());
        command(w.getServer().getCommandSource().withEntity(unconnected),bindCommand());unconnected.discard();
        c.assertTrue(snapshot().toString().equals(before)&&fingerprint().equals(blocks),"unprivileged modes/console/spoofed disconnected identity cannot bind, mutate or reward");
        c.assertTrue(site().isEmpty(),"unauthorized dispatcher attempts create no site");
        c.assertTrue(!owner.player.hasPermissionLevel(2)&&!foreign.player.hasPermissionLevel(2),"negative players remain genuinely unprivileged");
    }
    private void rightsExtraction(){
        if(phaseTicks==1){owner.player.changeGameMode(GameMode.CREATIVE);use(owner,herald,true);expect(owner,2,"READY",0,"Creative cannot extract actual ready Survival mission");return;}
        if(phaseTicks==2){owner.player.changeGameMode(GameMode.SPECTATOR);use(owner,herald,true);expect(owner,2,"READY",0,"Spectator cannot extract actual ready Survival mission");return;}
        if(instance(foreign).isEmpty()){
            if(!walk(foreign,home().add(1,0,0)))return;
            use(foreign,herald,true);expect(foreign,0,"ACTIVE",0,"other real owner receives only its own fresh uncompleted instance");
            expect(owner,2,"READY",0,"foreign use cannot take original owner's ready cursor or claim");return;
        }
        owner.player.changeGameMode(GameMode.SURVIVAL);use(owner,herald,true);claimed();
        expect(foreign,0,"ACTIVE",0,"first owner reward never leaks into another instance");next(15);
    }
    private void claimed(){
        int count=scenario==Case.SIDE?3:2;
        expect(owner,count,"CLAIMED",3,"only actual eligible Survival extraction grants fixed+3 exactly once");
        c.assertTrue(instance(owner).getUuid("owner").equals(owner.player.getUuid())&&instance(owner).getUuid("site").equals(siteId),"claim retains exact live owner and original site identity");
        if(repeatReceipt==null)repeatReceipt=instance(owner).toString();
    }
    private List<String> messages(Actor actor){
        var out=new ArrayList<String>();int chars=0;
        for(Object raw:actor.channel.outboundMessages())if(raw instanceof GameMessageS2CPacket packet){
            String text=packet.content().getString();if(out.size()>=64||chars+text.length()>65_536)break;
            out.add(text);chars+=text.length();
        }
        return List.copyOf(out);
    }
    private void verifyPackets(){
        var messages=messages(owner);String joined=String.join("\n",messages);
        c.assertTrue(!messages.isEmpty(),"actual normal end-tick packet delivery is visible without manual flush/pump");
        boolean claimed=scenario==Case.LAB||scenario==Case.SIDE||scenario==Case.RIGHTS;
        long extracts=messages.stream().filter(s->s.contains("Extracted "+definition+" (+3 miners)")).count();
        c.assertTrue(extracts==(claimed?1:0),"normal feedback agrees with exactly-once authoritative extraction, not acceptance: "+messages);
        c.assertTrue(joined.contains("Offer:")&&joined.contains("Accepted "+definition),"retained actual offer title and truthful acceptance feedback are delivered after state operation");
        if(claimed)c.assertTrue(joined.contains("Already extracted")&&rep(owner)==3,"repeat use reports existing receipt and retains only one reward");
        else c.assertTrue(rep(owner)==0&&rep(foreign)==0,"negative real-event case grants nobody reputation");
        record("normal-packets:"+messages);
    }
    private void record(String event){
        if(memory==null)return;
        var receipt=new LinkedHashMap<String,Object>();receipt.put("case",scenario);receipt.put("event",event);receipt.put("phase",phase);
        receipt.put("gameTick",c.getTick());receipt.put("serverTick",w.getServer().getTicks());receipt.put("definition",definition);
        receipt.put("herald",List.of(herald.getX(),herald.getY(),herald.getZ()));receipt.put("anchor",List.of(anchor.getX(),anchor.getY(),anchor.getZ()));
        receipt.put("owner",owner.player.getUuid().toString());receipt.put("ownerPos",owner.player.getPos().toString());receipt.put("ownerMode",owner.player.interactionManager.getGameMode().toString());
        receipt.put("ownerCursor",cursor(owner));receipt.put("ownerStatus",status(owner));receipt.put("ownerRep",rep(owner));receipt.put("site",siteId==null?"none":siteId.toString());
        receipt.put("commands",commandCount);receipt.put("uses",useCount);receipt.put("miningTicks",miningTicks);
        c.assertTrue(trace.size()<64,"bounded actual trace retains at most64 phase/event receipts");trace.add(receipt);
        System.out.println("ENCLAVE_GAMEPLAY_RECEIPT="+new com.google.gson.Gson().toJson(receipt));
    }
    private List<ItemEntity> items(){
        var out=new ArrayList<ItemEntity>();
        w.collectEntitiesByType(TypeFilter.instanceOf(ItemEntity.class),arenaBox(),entity->entity.isAlive(),out,33);
        c.assertTrue(out.size()<=32,"isolated fixture observes at most32 actual item entities");return out;
    }
    void close(Throwable original){
        if(closed)return;closed=true;Throwable failure=original;
        try{
            if(chunks!=null){
                for(var id:ownedDrops)if(w.getEntity(id) instanceof ItemEntity drop)drop.discard();
                var entries=new ArrayList<>(previous.entrySet());Collections.reverse(entries);
                for(var e:entries)w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_ALL);
            }
        }catch(RuntimeException|Error bad){failure=append(failure,bad);}
        for(var actor:actors){
            try{
                if(actor.operatorBefore!=null)w.getServer().getPlayerManager().getOpList().add(actor.operatorBefore);
                else if(w.getServer().getPlayerManager().isOperator(actor.player.getGameProfile()))w.getServer().getPlayerManager().removeFromOperators(actor.player.getGameProfile());
            }
            catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{if(w.getServer().getPlayerManager().getPlayer(actor.player.getUuid())==actor.player)w.getServer().getPlayerManager().remove(actor.player);actor.player.discard();}
            catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{actor.channel.finishAndReleaseAll();}catch(RuntimeException|Error bad){failure=append(failure,bad);}
        }
        try{if(chunks!=null)chunks.close();}catch(RuntimeException|Error bad){failure=append(failure,bad);}
        System.out.println("ENCLAVE_GAMEPLAY_CLOSED="+new com.google.gson.Gson().toJson(Map.of("case",scenario,"sourceFailure",original==null?"none":original.toString(),"restoredCells",previous.size(),"removedPlayers",actors.stream().allMatch(a->a.player.isRemoved()),"closedChannels",actors.stream().allMatch(a->!a.channel.isOpen()),"traceEvents",trace.size())));
        if(original==null){if(failure instanceof Error e)throw e;if(failure instanceof RuntimeException e)throw e;}
    }
    private static Throwable append(Throwable first,Throwable next){if(first==null)return next;if(first!=next)first.addSuppressed(next);return first;}
}
