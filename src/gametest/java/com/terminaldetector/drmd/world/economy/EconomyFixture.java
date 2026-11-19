package com.terminaldetector.drmd.world.economy;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.MechanicalBearingSystem;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.physics.collision.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.*;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.function.Consumer;
import java.nio.file.*;
import java.io.*;

/** Test-owned ordinary Survival connection, real crafting UI, material edits and timing.
 * Starting materials/table/carrier are fixtures, never a claim of ore mining or free equipment. */
final class EconomyFixture implements AutoCloseable {
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_economy_fixture",Comparator.<UUID>naturalOrder());
    final TestContext c;final ServerWorld w;final ServerPlayerEntity p;final BlockPos origin,table;
    final EmbeddedChannel channel;final UUID leaseId;final List<ChunkPos> columns;
    final List<BlockBodyEntity> bodies=new ArrayList<>();final Set<UUID> extras=new HashSet<>();
    final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
    final NavigableMap<Long,List<Runnable>> tasks=new TreeMap<>();
    Runnable observe=()->{};boolean closed;
    record Crafted(Identifier requested,Identifier actual,int count){}

    static void admitted(TestContext c,Consumer<EconomyFixture> start){
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,220,8));var id=UUID.randomUUID();var columns=new ArrayList<ChunkPos>();
        w.resetIdleTimeout();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var pos=new ChunkPos((at.getX()>>4)+x,(at.getZ()>>4)+z);columns.add(pos);w.getChunkManager().addTicket(TICKET,pos,2,id);}
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        EconomyFixture[] fixture={null};int[] wait={0};boolean[] started={false};
        // One repeating GameTest callback only; future steps are in our private queue.
        c.runAtEveryTick(()->{
            if(!started[0]){
                if(columns.stream().anyMatch(pos->!w.shouldTickEntity(pos.getStartPos()))){if(++wait[0]<80)return;started[0]=true;release(w,id,columns);c.assertTrue(false,"economy fixture requires entity-ticking columns");return;}
                started[0]=true;
                try{fixture[0]=new EconomyFixture(c,at,id,columns);start.accept(fixture[0]);}
                catch(Throwable failure){if(fixture[0]!=null)fixture[0].close();else release(w,id,columns);throw failure;}
            }
            var f=fixture[0];if(f==null||f.closed)return;
            try{
                c.assertTrue(c.getTick()<400,"economy fixture timed out before its real gameplay predicates completed");
                while(!f.tasks.isEmpty()&&f.tasks.firstKey()<=c.getTick()){var task=f.tasks.pollFirstEntry();for(var step:task.getValue())if(!f.closed)step.run();}
                if(!f.closed)f.observe.run();
            }catch(Throwable failure){f.close();throw failure;}
        });
    }
    private EconomyFixture(TestContext c,BlockPos origin,UUID id,List<ChunkPos> columns){
        this.c=c;w=c.getWorld();this.origin=origin;leaseId=id;this.columns=columns;table=origin.add(-5,0,-5);
        var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"economy-fixture"),false);
        p=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
        var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
        w.getServer().getPlayerManager().onPlayerConnect(connection,p,client);
        try{
            p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);p.getAbilities().flying=false;DescentPlayerData.get(p).setEnabled(false);
            p.getInventory().clear();setWorld(table,Blocks.CRAFTING_TABLE.getDefaultState());
            c.assertTrue(!p.isCreative()&&p.interactionManager.getGameMode()==GameMode.SURVIVAL,"ordinary Survival player, no permanently creative mock override");
            stock(Items.IRON_INGOT,64);stock(Items.COPPER_INGOT,64);stock(Items.REDSTONE,64);stock(Items.AMETHYST_SHARD,16);
            stock(Items.GOLD_INGOT,16);stock(Items.IRON_NUGGET,16);stock(Items.STICK,32);stock(Items.COBBLESTONE,32);
        }catch(Throwable failure){close();throw failure;}
    }
    private static void release(ServerWorld w,UUID id,List<ChunkPos> columns){for(var pos:columns)w.getChunkManager().removeTicket(TICKET,pos,2,id);}
    void after(int delay,Runnable step){tasks.computeIfAbsent(c.getTick()+delay,key->new ArrayList<>()).add(step);}
    void done(){close();c.complete();}
    void stock(Item item,int count){c.assertTrue(p.getInventory().insertStack(new ItemStack(item,count)),"declared starting material/tool fits inventory");}
    int count(Item item){int n=0;for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item))n+=p.getInventory().getStack(i).getCount();return n;}
    boolean has(Item item){return count(item)>0;}
    void hold(Item item){
        int slot=-1;for(int i=0;i<p.getInventory().size();i++)if(item==Items.AIR?p.getInventory().getStack(i).isEmpty():p.getInventory().getStack(i).isOf(item)){slot=i;break;}
        c.assertTrue(slot>=0,"only actual acquired inventory equipment may be held: "+Registries.ITEM.getId(item));
        int hand=p.getInventory().selectedSlot;var old=p.getInventory().getStack(hand);p.getInventory().setStack(hand,p.getInventory().getStack(slot));p.getInventory().setStack(slot,old);
    }
    void aim(Vec3d eye,Vec3d target){
        double height=p.getEyeY()-p.getY();p.setPosition(eye.subtract(0,height,0));p.setVelocity(Vec3d.ZERO);
        var d=target.subtract(p.getEyePos()).normalize();var q=PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));p.setYaw((float)q.yawDegrees());p.setPitch((float)q.pitchDegrees());p.setHeadYaw(p.getYaw());
    }
    void setWorld(BlockPos pos,BlockState state){previous.putIfAbsent(pos.toImmutable(),w.getBlockState(pos));w.setBlockState(pos,state,3);}
    private BlockHitResult worldHit(BlockPos block,Direction face){
        var point=Vec3d.ofCenter(block).add(Vec3d.of(face.getVector()).multiply(.5));
        // Approach side faces from above neighbouring supports, so the test player
        // does not stand inside another placed block while clicking the correct face.
        // A top-face click needs room for both the new block and the player below their eye.
        double clearance = face == Direction.UP ? 3 : 2;
        var stand = point.add(Vec3d.of(face.getVector()).multiply(clearance));
        if (face.getAxis().isHorizontal()) stand = stand.add(0, 2.5, 0);
        aim(stand,point);return new BlockHitResult(point,face,block,false);
    }
    void placeWorld(BlockPos support,Direction face,Item item){
        hold(item);int before=count(item);var hit=worldHit(support,face);var target=support.offset(face);previous.putIfAbsent(target,w.getBlockState(target));
        c.assertTrue(p.interactionManager.interactBlock(p,w,p.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"actual Survival BlockItem use accepted");
        c.assertTrue(w.getBlockState(target).isOf(((BlockItem)item).getBlock())&&count(item)==before-1,"actual world placement consumes exactly one crafted item");
    }
    private CraftingScreenHandler table(){
        if(p.currentScreenHandler instanceof CraftingScreenHandler h)return h;
        hold(Items.AIR);var hit=worldHit(table,Direction.UP);c.assertTrue(p.interactionManager.interactBlock(p,w,p.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"real crafting table opens");
        c.assertTrue(p.currentScreenHandler instanceof CraftingScreenHandler,"Vanilla opened its actual server crafting handler");return (CraftingScreenHandler)p.currentScreenHandler;
    }
    private Map<Item,Integer> inventory(){var counts=new HashMap<Item,Integer>();for(int i=0;i<p.getInventory().size();i++){var s=p.getInventory().getStack(i);if(!s.isEmpty())counts.merge(s.getItem(),s.getCount(),Integer::sum);}return counts;}
    private void click(CraftingScreenHandler h,int slot,int button){h.onSlotClick(slot,button,SlotActionType.PICKUP,p);}
    private void fill(CraftingScreenHandler h,ShapedRecipe recipe,Map<Item,Integer> used,Item omit){
        c.assertTrue(h.getCursorStack().isEmpty(),"crafting cursor starts empty");for(int i=1;i<=9;i++)c.assertTrue(h.getSlot(i).getStack().isEmpty(),"previous craft left no input or ghost ingredients");
        for(int y=0;y<recipe.getHeight();y++)for(int x=0;x<recipe.getWidth();x++){
            var ingredient=recipe.getIngredients().get(y*recipe.getWidth()+x);if(ingredient.isEmpty())continue;
            Slot found=null;for(var slot:h.slots)if(slot.inventory==p.getInventory()&&ingredient.test(slot.getStack())){found=slot;break;}
            c.assertTrue(found!=null,"missing Survival ingredient at "+x+","+y+": "
                +Arrays.toString(ingredient.getMatchingStacks()));var item=found.getStack().getItem();if(item==omit)continue;used.merge(item,1,Integer::sum);
            click(h,found.id,0);click(h,1+y*3+x,1);if(!h.getCursorStack().isEmpty())click(h,found.id,0);
        }
    }
    Crafted craft(String path,boolean requireRequested){
        var id=path.contains(":")?Identifier.of(path):Identifier.of("drmd",path);var entry=w.getRecipeManager().get(id).orElseThrow();c.assertTrue(entry.value() instanceof ShapedRecipe,"existing production shaped recipe "+id);var recipe=(ShapedRecipe)entry.value();
        var h=table();var before=inventory();var used=new HashMap<Item,Integer>();fill(h,recipe,used,Items.AIR);
        var actual=h.getSlot(0).getStack().copy();c.assertTrue(!actual.isEmpty(),"Vanilla crafting UI produced an actual result for "+id);click(h,0,0);
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(actual,h.getCursorStack())&&actual.getCount()==h.getCursorStack().getCount(),"real result slot take, not a recipe.craft grant");
        Slot target=null;for(var slot:h.slots)if(slot.inventory==p.getInventory()&&(slot.getStack().isEmpty()||ItemStack.areItemsAndComponentsEqual(slot.getStack(),actual)&&slot.getStack().getCount()+actual.getCount()<=actual.getMaxCount())){target=slot;break;}
        c.assertTrue(target!=null,"crafted result has inventory capacity");click(h,target.id,0);c.assertTrue(h.getCursorStack().isEmpty(),"actual crafted result was deposited through the crafting UI");
        var expected=new HashMap<>(before);used.forEach((item,n)->expected.merge(item,-n,Integer::sum));expected.merge(actual.getItem(),actual.getCount(),Integer::sum);expected.entrySet().removeIf(e->e.getValue()==0);
        c.assertTrue(inventory().equals(expected),"actual recipe take consumes precisely the loaded ingredients and preserves all other material");
        var result=new Crafted(id,Registries.ITEM.getId(actual.getItem()),actual.getCount());
        if(requireRequested)c.assertTrue(actual.isOf(recipe.getResult(w.getRegistryManager()).getItem()),"requested recipe must be reachable through the ordinary UI: "+result);
        return result;
    }
    void craft(String path){craft(path,true);}
    void incomplete(String path,Item omit){
        var entry=w.getRecipeManager().get(Identifier.of("drmd",path)).orElseThrow();var h=table();var before=inventory();
        fill(h,(ShapedRecipe)entry.value(),new HashMap<>(),omit);c.assertTrue(h.getSlot(0).getStack().isEmpty(),"missing actual ingredient cannot create a crafting result");
        closeTable();c.assertTrue(inventory().equals(before),"closing an incomplete real crafting grid refunds precisely its input material");
    }
    void components(int plates,int cells){for(int i=0;i<(plates+1)/2;i++)craft("alloy_plate");for(int i=0;i<(cells+1)/2;i++)craft("energy_cell");}
    void closeTable(){if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();}
    String diagnostic(String name,String text){
        var path=Path.of("reports",name+".txt");
        try{Files.createDirectories(path.getParent());Files.writeString(path,text+"\n");}
        catch(IOException failure){throw new UncheckedIOException("Cannot preserve actual economy diagnostic",failure);}
        return path.toString();
    }
    BlockBodyEntity carrier(){
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=-2;x<=8;x++)for(int z=-1;z<=3;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState()));
        for(int x=0;x<=6;x+=2)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        var b=BlockBodyEntity.createLocal(w,cells,Vec3d.of(origin),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);b.setPhysicsGravity(false);bodies.add(b);return b;
    }
    void aimCell(BlockBodyEntity b,BlockPos cell,Direction face){
        var normal=b.rotation().rotate(new Vec3(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()));var v=new Vec3d(normal.x(),normal.y(),normal.z());var point=b.localToWorld(Vec3d.ofCenter(cell).add(Vec3d.of(face.getVector()).multiply(.5)));
        // Upward placement reserves a complete destination cube. The old2.5m
        // eye offset left0.12m of the ordinary1.62m eye-height player's box in it.
        var stand=point.add(v.multiply(face==Direction.UP?3:2.5));if(face.getAxis().isHorizontal())stand=stand.add(0,2.2,0);aim(stand,point);
    }
    void placeLocal(BlockBodyEntity b,BlockPos support,Direction face,Item item){
        closeTable();hold(item);int before=count(item);aimCell(b,support,face);
        var ray=BodyBlockInteraction.target(p,p.getEyePos(),BodyBlockInteraction.viewDirection(p),p.getBlockInteractionRange());var target=support.offset(face);
        var cube=OrientedBox.local(new BodyBounds(new Vec3(target.getX(),target.getY(),target.getZ()),new Vec3(target.getX()+1,target.getY()+1,target.getZ()+1)),b.massPosition(),b.localCentre(),b.rotation());var bounds=cube.bounds();
        var broad=new Box(bounds.min().x(),bounds.min().y(),bounds.min().z(),bounds.max().x(),bounds.max().y(),bounds.max().z());boolean available=b.collisionRegionAvailable(broad);var contacts=new ArrayList<String>();
        if(available){
            for(var shape:w.getBlockCollisions(b,broad.contract(1e-6)))contacts.add("world="+shape.getBoundingBoxes());
            for(var e:BodySpatialIndex.otherEntities(w,b,broad,e->e.isAlive()&&!e.isSpectator())){
                if(contacts.size()>=16)break;
                if(e instanceof BlockBodyEntity other)contacts.add("body="+other.getUuid()+" "+other.collisionQuery().contacts(cube,0));
                else{
                    var box=e.getBoundingBox();var actor=e instanceof net.minecraft.entity.player.PlayerEntity player
                        ?PassengerFrame.box(BoardingSystem.pure(player.getPos()),player.getWidth(),player.getHeight(),BoardingPose.frame(player))
                        :OrientedBox.axisAligned(new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)));
                    contacts.add("entity="+e.getType()+" pos="+e.getPos()+" frame="+(e==p?BoardingPose.frame(p):null)+" contact="+cube.contact(actor,0));
                }
            }
        }
        String report=diagnostic("economy-placement-"+b.getUuid()+"-"+c.getTick(),"serverTick="+w.getServer().getTicks()+" testTick="+c.getTick()+" support="+support+" face="+face+" target="+target+" revision="+b.shapeRevision()+" existing="+b.getLocalBlock(target)+" item="+p.getMainHandStack()+" player="+p.getPos()+" eye="+p.getEyePos()+" range="+p.getBlockInteractionRange()+" view="+BodyBlockInteraction.viewDirection(p)+" ray="+ray+" regionAvailable="+available+" cube="+broad+" contacts="+contacts);
        c.assertTrue(BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),b.shapeRevision(),support,BodyBlockInteraction.PLACE)),"actual validated local placement "+support+" "+face+"; diagnostic="+report);
        c.assertTrue(b.getLocalBlock(support.offset(face)).isOf(((BlockItem)item).getBlock())&&count(item)==before-1,"Survival local grid consumes exactly one actual crafted item");
    }
    void useLocal(BlockBodyEntity b,BlockPos cell,Direction face){
        closeTable();aimCell(b,cell,face);c.assertTrue(BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),b.shapeRevision(),cell,BodyBlockInteraction.USE)),"actual validated local material use");
    }
    void itemUse(){closeTable();c.assertTrue(p.interactionManager.interactItem(p,w,p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"actual held crafted item use");}
    BlockBodyEntity assemble(BlockPos min,BlockPos max){
        closeTable();hold(ModWorldBlocks.MECHANISM_WRENCH);var before=new HashSet<UUID>();for(var b:BodySpatialIndex.loaded(w))before.add(b.getUuid());
        p.interactionManager.interactBlock(p,w,p.getMainHandStack(),Hand.MAIN_HAND,worldHit(min,Direction.UP));
        p.interactionManager.interactBlock(p,w,p.getMainHandStack(),Hand.MAIN_HAND,worldHit(max,Direction.UP));
        var created=BodySpatialIndex.loaded(w).stream().filter(b->!before.contains(b.getUuid())).toList();c.assertTrue(created.size()==1,"two actual wrench clicks assemble one physical body");var b=created.getFirst();b.setPhysicsGravity(false);bodies.add(b);return b;
    }
    public void close(){
        if(closed)return;closed=true;tasks.clear();
        BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(new UUID(0,0),0,BlockPos.ORIGIN,BodyBlockInteraction.STOP));
        if(has(ModWorldBlocks.MECHANISM_WRENCH)){hold(ModWorldBlocks.MECHANISM_WRENCH);worldHit(table,Direction.UP);p.setSneaking(true);MechanicalBearingSystem.useWorld(p,table);p.setSneaking(false);}
        var owners=new HashSet<UUID>();owners.add(p.getUuid());for(var b:bodies)owners.add(b.getUuid());
        for(var e:w.iterateEntities())if(e instanceof ProjectileEntity shot&&shot.getOwnerEntity()!=null&&owners.contains(shot.getOwnerEntity().getUuid()))extras.add(e.getUuid());
        for(var id:extras){var e=w.getEntity(id);if(e!=null)e.discard();}
        for(var b:bodies){BodyJointRegistry.get(w).removeBody(b.getUuid());b.discard();}
        for(var e:previous.entrySet())w.setBlockState(e.getKey(),e.getValue(),3);
        try{closeTable();w.getServer().getPlayerManager().remove(p);p.discard();}finally{channel.finishAndReleaseAll();release(w,leaseId,columns);}
    }
}
