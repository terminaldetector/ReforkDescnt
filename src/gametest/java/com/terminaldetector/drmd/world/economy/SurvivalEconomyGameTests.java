package com.terminaldetector.drmd.world.economy;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.util.Hand;
import java.util.*;

/** Actual Survival recipe/result-slot acquisition precedes use. No product grants,
 * injected weapon targets, manual entity ticks, invented fuel costs or production changes. */
public final class SurvivalEconomyGameTests implements FabricGameTest {
    private static Item item(String path){return Registries.ITEM.get(Identifier.of("drmd",path));}
    private static BlockPos cell(BlockBodyEntity b,Block block){return b.cells().stream().filter(c->c.state().isOf(block)).findFirst().orElseThrow().pos();}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-real-crafting",tickLimit=450)
    public void allThreeShipComponentsMustBeIndividuallyCraftableThroughTheRealTable(TestContext c){EconomyFixture.admitted(c,f->{
        f.components(24,3);var actual=new ArrayList<EconomyFixture.Crafted>();
        // Do all three real attempts before reporting the collision. Never select a
        // specific recipe by calling craft() directly or grant its declared output.
        for(var id:List.of("ship_laser","ship_rocket","ship_thruster"))actual.add(f.craft(id,false));
        f.components(8,0);f.incomplete("ship_laser",item("energy_cell"));
        c.assertTrue(actual.stream().allMatch(out->out.requested().equals(out.actual()))
            &&actual.stream().map(EconomyFixture.Crafted::actual).distinct().count()==3,
            "all three recipes must be reachable by normal crafting, with conserved material; actual UI results="+actual);
        f.done();
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-mechanism",tickLimit=450)
    public void craftedPlacedAndAssembledBearingsAndDrivePerformRealSurvivalWork(TestContext c){EconomyFixture.admitted(c,f->{
        f.craft("mechanism_wrench");f.craft("mechanical_bearing");f.craft("mechanical_bearing");f.craft("rotary_drive");f.closeTable();
        var root=f.origin;f.setWorld(root.west(2),Blocks.STONE.getDefaultState());f.setWorld(root.east(2),Blocks.STONE.getDefaultState());
        f.placeWorld(root.west(2),Direction.EAST,ModWorldBlocks.ROTARY_DRIVE.asItem());
        f.placeWorld(root.west(),Direction.EAST,ModWorldBlocks.MECHANICAL_BEARING.asItem());
        f.placeWorld(root.east(2),Direction.WEST,ModWorldBlocks.MECHANICAL_BEARING.asItem());
        var a=f.assemble(root.west(),root);var b=f.assemble(root.east(),root.east());
        c.assertTrue(f.w.getBlockState(root).isAir()&&f.w.getBlockState(root.east()).isAir(),"assembly moves actual placed material into local grids without keeping static copies");
        c.assertTrue(f.count(ModWorldBlocks.MECHANICAL_BEARING.asItem())==0&&f.count(ModWorldBlocks.ROTARY_DRIVE.asItem())==0,"neither placement nor assembly refunds or duplicates crafted components");
        var aId=a.getUuid();var bId=b.getUuid();f.hold(ModWorldBlocks.MECHANISM_WRENCH);f.useLocal(a,cell(a,ModWorldBlocks.MECHANICAL_BEARING),Direction.UP);
        f.after(5,()->{
            f.hold(ModWorldBlocks.MECHANISM_WRENCH);f.useLocal(b,cell(b,ModWorldBlocks.MECHANICAL_BEARING),Direction.UP);
            c.assertTrue(BodyJointRegistry.get(f.w).forBody(aId).size()==1,"two real local wrench uses connect the separately assembled bearings");
            f.after(5,()->{
                f.hold(ModWorldBlocks.MECHANISM_WRENCH);f.useLocal(a,cell(a,ModWorldBlocks.ROTARY_DRIVE),Direction.UP);
                var link=BodyJointRegistry.get(f.w).forBody(aId).getFirst();c.assertTrue(link.speed()>0&&link.motorForce()>0,"crafted rotary drive controls the real persistent motor");
                f.after(25,()->{c.assertTrue(a.getUuid().equals(aId)&&b.getUuid().equals(bId)
                    &&(a.physics().angularMomentum().length()>1e-7||b.physics().angularMomentum().length()>1e-7),"existing server physics performs motor work on the same acquired bodies, without assigned rotation");f.done();});
            });
        });
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-mounted-fire",tickLimit=450)
    public void craftedLaserAndRocketConsumePlacementItemsAndFireFromActualLeverMaterial(TestContext c){EconomyFixture.admitted(c,f->{
        f.components(16,2);f.craft("ship_laser");f.craft("ship_rocket");f.craft("minecraft:lever");f.craft("minecraft:lever");
        var b=f.carrier();var laser=new BlockPos(0,0,1);var rocket=new BlockPos(2,0,1);var seenLaser=new HashSet<UUID>();var seenRocket=new HashSet<UUID>();
        f.observe=()->{for(var shot:f.w.getEntitiesByClass(ProjectileEntity.class,b.getBoundingBox().expand(160),s->s.getOwnerEntity()==b))(shot.getMeshKind()==1?seenRocket:seenLaser).add(shot.getUuid());};
        f.placeLocal(b,laser.north(),Direction.SOUTH,ModWorldBlocks.SHIP_LASER.asItem());
        f.after(5,()->{f.placeLocal(b,rocket.north(),Direction.SOUTH,ModWorldBlocks.SHIP_ROCKET.asItem());
            f.after(5,()->{f.placeLocal(b,laser,Direction.UP,Items.LEVER);
                f.after(5,()->{f.placeLocal(b,rocket,Direction.UP,Items.LEVER);
                    f.after(5,()->{f.hold(Items.AIR);f.useLocal(b,laser.up(),Direction.UP);
                        f.after(5,()->{f.hold(Items.AIR);f.useLocal(b,rocket.up(),Direction.UP);
                            f.after(40,()->{
                                c.assertTrue(seenLaser.size()>=3&&seenRocket.size()>=1,"actual post-contact schedule fires acquired laser/rocket, no direct emitter calls: "+seenLaser.size()+" / "+seenRocket.size());
                                c.assertTrue(f.count(ModWorldBlocks.SHIP_LASER.asItem())==0&&f.count(ModWorldBlocks.SHIP_ROCKET.asItem())==0,"installed crafted barrels are material, not duplicated inventory aliases");f.done();
                            });
                        });
                    });
                });
            });
        });
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-salvage",tickLimit=450)
    public void survivalMiningPickupAndReplacementRecoverBearingDriveAndLaserExactlyOnce(TestContext c){EconomyFixture.admitted(c,f->{
        f.components(8,1);f.craft("mechanical_bearing");f.craft("rotary_drive");f.craft("ship_laser");f.stock(Items.DIAMOND_PICKAXE,1);
        var b=f.carrier();var items=List.of(ModWorldBlocks.MECHANICAL_BEARING.asItem(),ModWorldBlocks.ROTARY_DRIVE.asItem(),ModWorldBlocks.SHIP_LASER.asItem());
        for(int i=0;i<items.size();i++){final int index=i;f.after(i*5,()->f.placeLocal(b,new BlockPos(index*2,0,0),Direction.SOUTH,items.get(index)));}
        int[] index={0};int[] phase={0};long[] ready={c.getTick()+20};var checked=new HashSet<Integer>();
        f.observe=()->{
            if(c.getTick()<ready[0])return;var equipment=items.get(index[0]);var target=new BlockPos(index[0]*2,0,1);
            if(phase[0]==0){
                f.hold(Items.DIAMOND_PICKAXE);f.aimCell(b,target,Direction.SOUTH);
                if(checked.add(index[0])){
                    var state=b.getLocalBlock(target);boolean harvest=f.p.canHarvest(state);
                    String report=f.diagnostic("economy-salvage-"+index[0],"state="+state+" held="+f.p.getMainHandStack()+" canHarvest="+harvest+" requiresTool="+state.isToolRequired()+" miningSpeed="+f.p.getBlockBreakingSpeed(state)+" onGround="+f.p.isOnGround()+" pickaxeTag="+state.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE)+" revision="+b.shapeRevision());
                    c.assertTrue(harvest,"real declared diamond pickaxe must harvest recoverable component; diagnostic="+report);
                }
                if(!b.getLocalBlock(target).isAir()){
                    if(c.getTick()%3==0)c.assertTrue(BodyBlockInteraction.receive(f.p,new BodyBlockInteraction.Edit(b.getUuid(),b.shapeRevision(),target,BodyBlockInteraction.MINE)),"real Survival mining pulses remain valid until material breaks");return;
                }
                c.assertTrue(f.count(equipment)==0,"mining did not directly grant an inventory copy");phase[0]=1;ready[0]=c.getTick()+12;return;
            }
            if(phase[0]==1){
                var drops=f.w.getEntitiesByClass(ItemEntity.class,b.getBoundingBox().expand(5),drop->drop.getStack().isOf(equipment));
                c.assertTrue(drops.size()==1&&drops.getFirst().getStack().getCount()==1,"real component loot table yields one recoverable item: "+Registries.ITEM.getId(equipment));
                var drop=drops.getFirst();f.extras.add(drop.getUuid());f.p.setPosition(drop.getPos());drop.onPlayerCollision(f.p);
                c.assertTrue(drop.isRemoved()&&f.count(equipment)==1,"actual vanilla item pickup transfers that single recovered component");
                phase[0]=2;ready[0]=c.getTick()+5;return;
            }
            f.placeLocal(b,target.north(),Direction.SOUTH,equipment);c.assertTrue(f.count(equipment)==0,"reusing recovered material consumes it once");
            if(++index[0]==items.size()){f.done();return;}phase[0]=0;ready[0]=c.getTick()+5;
        };
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-local-repair",tickLimit=450)
    public void craftedRepairLaserMustRepairActualPartialDamageOnAMovingMechanism(TestContext c){EconomyFixture.admitted(c,f->{
        // Independent of the colliding ship-component recipes: the existing handheld
        // MG causes real projectile damage; the repair tool is acquired through its own UI.
        f.components(6,1);f.craft("weapon_d6_mg");f.craft("repair_laser");f.craft("rotary_drive");var b=f.carrier();var drive=new BlockPos(0,0,1);
        f.placeLocal(b,BlockPos.ORIGIN,Direction.SOUTH,ModWorldBlocks.ROTARY_DRIVE.asItem());
        f.after(5,()->{
            f.hold(item("weapon_d6_mg"));f.aimCell(b,drive,Direction.SOUTH);
            var def=((DescentWeaponItem)f.p.getMainHandStack().getItem()).getDef();var direction=WeaponCore.aimDir(f.p).normalize();
            var muzzleBefore=WeaponCore.muzzleFor(f.p,def.id,1);var toward=b.localToWorld(Vec3d.ofCenter(drive)).subtract(muzzleBefore);
            // MG's real first mount is one metre sideways and0.625m down from the
            // view axis. Position the test shooter so that actual barrel, not a
            // substituted projectile ray, points through the selected material.
            var correction=toward.subtract(direction.multiply(toward.dotProduct(direction)));f.p.setPosition(f.p.getPos().add(correction));
            var muzzle=WeaponCore.muzzleFor(f.p,def.id,1);var bore=new BlockBodyWorldAccess(b).raycastBlockBody(muzzle,muzzle.add(direction.multiply(16)));
            String aim="eye="+f.p.getEyePos()+" view="+BodyBlockInteraction.viewDirection(f.p)+" actualAim="+direction+" validAttitude="+DescentPlayerData.get(f.p).hasShipAttitude()+" primaryMuzzle="+muzzle+" shooterCorrection="+correction+" bore="+bore;
            String aimReport=f.diagnostic("economy-repair-aim",aim);
            c.assertTrue(bore.isPresent()&&bore.get().localBlock().equals(drive),"real primary barrel must reach selected drive before firing; diagnostic="+aimReport);
            var oldShots=new HashSet<UUID>();for(var e:f.w.iterateEntities())if(e instanceof ProjectileEntity)oldShots.add(e.getUuid());
            var shots=new LinkedHashMap<UUID,ProjectileEntity>();var trail=new ArrayList<String>();long fired=c.getTick();boolean[] observed={false};float energyBefore=DescentPlayerData.get(f.p).getEnergy();
            Runnable sample=()->{
                for(var e:f.w.iterateEntities())if(e instanceof ProjectileEntity shot&&!oldShots.contains(e.getUuid())&&shots.size()<8){shots.putIfAbsent(e.getUuid(),shot);f.extras.add(e.getUuid());}
                if(trail.size()<24)for(var shot:shots.values())if(trail.size()<24)trail.add("t="+(c.getTick()-fired)+" id="+shot.getUuid()+" owner="+(shot.getOwnerEntity()==null?null:shot.getOwnerEntity().getUuid())+" p="+shot.getPos()+" v="+shot.getVelocity()+" alive="+shot.isAlive()+" age="+shot.age);
            };
            f.itemUse();float energyAfter=DescentPlayerData.get(f.p).getEnergy();sample.run();
            java.util.function.Supplier<String> diagnostic=()->aim+" energy="+energyBefore+"->"+energyAfter+" target="+b.getLocalBlock(drive)+" damaged="+b.cells().stream().filter(cell->b.materialDamage(cell.pos())>0).map(cell->cell.pos()+":"+b.materialDamage(cell.pos())).toList()+" projectiles="+trail;
            f.observe=()->{
                if(observed[0])return;sample.run();double damage=b.materialDamage(drive);
                if(damage<=0){if(c.getTick()-fired>=100){String report=f.diagnostic("economy-repair-projectiles",diagnostic.get());c.assertTrue(false,"actual crafted MG projectile must first damage the selected physical drive; diagnostic="+report);}return;}
                observed[0]=true;c.assertTrue(b.getLocalBlock(drive).isOf(ModWorldBlocks.ROTARY_DRIVE),"damage is partial, not destruction/replacement");
                f.diagnostic("economy-repair-projectiles",diagnostic.get());
                var id=b.getUuid();double mass=b.physicsMass();var collider=b.collider();long revision=b.shapeRevision();
                c.assertTrue(b.physics().linearVelocity().length()>1e-10||b.physics().angularMomentum().length()>1e-10,"the real impact gives the mechanism actual physical motion before repair");
                f.hold(ModWorldBlocks.REPAIR_LASER);f.aimCell(b,drive,Direction.SOUTH);
                var coverAt=BlockPos.ofFloored(f.p.getEyePos().lerp(b.localToWorld(Vec3d.ofCenter(drive)),.5));var old=f.w.getBlockState(coverAt);f.setWorld(coverAt,Blocks.BEDROCK.getDefaultState());f.itemUse();
                c.assertTrue(b.materialDamage(drive)==damage,"actual world cover blocks repair of the damaged local cell");f.setWorld(coverAt,old);
                var middle=f.p.getEyePos().lerp(b.localToWorld(Vec3d.ofCenter(drive)),.5).subtract(.5,.5,.5);
                var front=BlockBodyEntity.createLocal(f.w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),middle,Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);front.setPhysicsGravity(false);f.bodies.add(front);
                f.p.interactionManager.interactItem(f.p,f.w,f.p.getMainHandStack(),Hand.MAIN_HAND);
                c.assertTrue(b.materialDamage(drive)==damage,"an intact nearer physical body cannot be passed through to repair a farther hull");front.discard();
                f.p.changeGameMode(GameMode.SPECTATOR);f.p.interactionManager.interactItem(f.p,f.w,f.p.getMainHandStack(),Hand.MAIN_HAND);f.p.changeGameMode(GameMode.SURVIVAL);
                c.assertTrue(b.materialDamage(drive)==damage,"spectator use does not repair local material");
                c.assertTrue(!b.repairLocalMaterial(new BlockPos(100,100,100)),"absent local cells cannot be repaired or fabricated");
                var velocity=b.physics().linearVelocity();var angular=b.physics().angularMomentum();f.aimCell(b,drive,Direction.SOUTH);f.itemUse();
                c.assertTrue(b.materialDamage(drive)<damage,"actual crafted repair use must reduce local mechanism damage; before="+damage+" after="+b.materialDamage(drive)+"; diagnostic=reports/economy-repair-projectiles.txt");
                c.assertTrue(b.getUuid().equals(id)&&b.physicsMass()==mass&&b.shapeRevision()==revision&&b.collider()==collider&&b.physics().linearVelocity().equals(velocity)&&b.physics().angularMomentum().equals(angular),"partial repair preserves body identity, grid, collider, material mass and physical motion");f.done();
            };
        });
    });}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="economy-material-repair",tickLimit=450)
    public void craftedRepairMustPreserveStoneDeepslateAndNetherBrickMaterialFamilies(TestContext c){EconomyFixture.admitted(c,f->{
        f.components(3,1);f.craft("repair_laser");f.closeTable();f.hold(ModWorldBlocks.REPAIR_LASER);
        var cracked=List.of(Blocks.CRACKED_STONE_BRICKS,Blocks.CRACKED_DEEPSLATE_BRICKS,Blocks.CRACKED_NETHER_BRICKS);
        var repaired=List.of(Blocks.STONE_BRICKS,Blocks.DEEPSLATE_BRICKS,Blocks.NETHER_BRICKS);var actual=new ArrayList<String>();boolean correct=true;
        for(int i=0;i<cracked.size();i++){
            var at=f.origin.add(i*3,0,0);f.setWorld(at,cracked.get(i).getDefaultState());var center=Vec3d.ofCenter(at);f.aim(center.add(0,0,3),center);f.itemUse();
            var state=f.w.getBlockState(at);actual.add(Registries.BLOCK.getId(cracked.get(i))+" -> "+Registries.BLOCK.getId(state.getBlock()));correct&=state.isOf(repaired.get(i));
        }
        c.assertTrue(correct,"repair restores each actual source material instead of converting unrelated bricks into stone: "+actual);f.done();
    });}
}
