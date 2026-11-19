package com.terminaldetector.drmd;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;
/** Existing quiet-support AI and ordinary collision must agree with a visible retained-solid cavity. */
public final class HybridNavigationGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hybrid-shaped-navigation",tickLimit=280)
    public void ordinarySupportEntersTheActualCarvedVolumeCrossesAndHealsWithoutRemovingItsShell(TestContext ctx){
        var w=ctx.getWorld();var base=ctx.getAbsolutePos(new BlockPos(12000,160,4000));var leases=new AiFixtureChunks(w,base,2);
        var authored=new ArrayList<BlockPos>();var shells=new LinkedHashMap<BlockPos,Long>();
        for(var p:BlockPos.iterate(base.add(-4,-4,-4),base.add(5,4,4)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++){
            var at=base.add(0,y,z);authored.add(at);w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);final int by=y,bz=z;
            long mask=MicroGrid.build((x,a,b)->Math.pow(by+(a+.5)*.25,2)+Math.pow(bz+(b+.5)*.25,2)>.9*.9);
            if(mask!=MicroGrid.FULL){ctx.assertTrue(mask!=0,"round opening retains actual material in each participating block");ctx.assertTrue(CarvedBlock.replace(w,at,Blocks.IRON_BLOCK.getDefaultState(),mask)!=null,"normal carved authoring");shells.put(at,mask);}
        }
        ctx.assertTrue(shells.size()>=4,"actual opening crosses partial carriers rather than replacing it with an AIR cube");
        var support=ModEntities.DRONE.create(w);var patient=ModEntities.DRONE.create(w);support.applyRole(AiRole.SUPPORT);patient.applyRole(AiRole.MG);
        var start=new Vec3d(base.getX()-2.5,base.getY()-support.getHeight()*.5,base.getZ());var goal=start.add(6,0,0);
        support.setPosition(start);patient.setPosition(goal);support.setPersistent();patient.setPersistent();support.setAiDisabled(true);patient.setAiDisabled(true);
        patient.setHealth(patient.getMaxHealth()*.25f);float initial=patient.getHealth();
        boolean[] spawned={false},active={false},done={false},entered={false};int[] admission={0},ticks={0};
        Runnable cleanup=()->{if(done[0])return;done[0]=true;support.discard();patient.discard();for(var at:authored)w.setBlockState(at,Blocks.AIR.getDefaultState(),2);leases.close();};
        ctx.runAtEveryTick(()->{if(done[0])return;try{
            w.resetIdleTimeout();
            if(!spawned[0]){ctx.assertTrue(++admission[0]<80,"ordinary entity-ticking admission");if(!leases.entityTicking(support.getBlockPos(),patient.getBlockPos()))return;ctx.assertTrue(w.spawnEntity(support)&&w.spawnEntity(patient),"actual support roles spawned");spawned[0]=true;return;}
            if(!active[0]){ctx.assertTrue(++admission[0]<80,"actual actor admission");if(support.age<3||patient.age<3||w.getEntity(support.getUuid())!=support||w.getEntity(patient.getUuid())!=patient)return;ctx.assertTrue(support.getTarget()==null&&patient.getTarget()==null,"no synthetic enemy/target injection");support.setAiDisabled(false);active[0]=true;}
            int tick=++ticks[0];var box=support.getBoundingBox();
            ctx.assertTrue(support.isAlive()&&support.getRole()==AiRole.SUPPORT&&support.getTarget()==null,"normal targetless support goal remains active");
            ctx.assertTrue(!w.getBlockCollisions(support,box.contract(1e-5)).iterator().hasNext(),"normal motion never overlaps the rendered retained solid");
            for(var at:shells.keySet())if(box.intersects(new Box(at)))entered[0]=true;
            for(var entry:shells.entrySet())ctx.assertTrue(w.getBlockEntity(entry.getKey())instanceof CarvedBlockEntity c&&c.mask()==entry.getValue(),"navigation never removes or edits cavity material");
            boolean crossed=box.minX>base.getX()+1+1e-5;
            if(crossed&&entered[0]&&patient.getHealth()>initial){System.out.println("HYBRID_CAVITY_NAV_PASS ticks="+tick+" support="+support.getPos()+" patientHealth="+patient.getHealth()+" realCarvedCells="+shells.size());cleanup.run();ctx.complete();return;}
            ctx.assertTrue(tick<180,"ordinary support crosses shaped carriers and heals: tick="+tick+" at="+support.getPos()+" entered="+entered[0]+" patient="+patient.getHealth());
        }catch(RuntimeException|Error failure){cleanup.run();throw failure;}});
    }
}
