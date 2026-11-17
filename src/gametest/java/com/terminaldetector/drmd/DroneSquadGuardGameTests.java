package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.Consumer;

/** Guards start from an actual naturally published observation; never inject mail or targets. */
public final class DroneSquadGuardGameTests implements FabricGameTest {
    private static final class State implements AutoCloseable {
        final TestContext c;
        final DroneSquadTaskGameTests.Rig rig;
        final List<Runnable> cleanup=new ArrayList<>();
        DroneSquadMailbox.Message original;
        long started;
        int waiting,phase;
        State(TestContext c,int offset){this.c=c;rig=new DroneSquadTaskGameTests.Rig(c,true,1024+offset);}
        long now(){return c.getWorld().getTime();}
        void bounds(){
            var s=DroneSquadCoordinator.stats(c.getWorld());
            c.assertTrue(s.mailboxes()<=256&&s.maxAllies()<=32&&s.maxCandidates()<=96&&s.cleanupExamined()<=32,"actual coordinator work remains bounded: "+s);
            c.assertTrue(s.publications()>0&&s.deliveries()>0,"real observed mail, not an absent-feature zero counter: "+s);
        }
        void sealVictim(){
            var hidden=rig.victim.getPos().add(0,0,32);var centre=BlockPos.ofFloored(hidden);
            for(int x=-2;x<=2;x++)for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||y==-2||y==3||Math.abs(z)==2){
                var pos=centre.add(x,y,z);rig.blocks.putIfAbsent(pos,c.getWorld().getBlockState(pos));c.getWorld().setBlockState(pos,Blocks.BEDROCK.getDefaultState(),2);
            }
            c.assertTrue(rig.wingman.getTarget()==null,"receiver has not acquired its own target before the hidden-position control");
            rig.victim.setPosition(hidden); // One deliberate fixture move, never a following pose rewrite.
            c.assertTrue(!rig.scout.canSee(rig.victim)&&!rig.wingman.canSee(rig.victim),"new position is opaque to both real actors");
        }
        void complete(){close();c.complete();}
        public void close(){try{for(var action:cleanup)action.run();cleanup.clear();}finally{rig.close();}}
    }
    private void run(TestContext c,int offset,Consumer<State> action){
        var state=new State(c,offset);
        c.runAtEveryTick(()->{
            if(state.rig.closed)return;
            try{
                c.getWorld().resetIdleTimeout();if(!state.rig.admitted())return;
                if(state.original==null){
                    var message=DroneSquadCoordinator.message(state.rig.wingman);
                    if(message.isEmpty()){
                        if(++state.waiting>=80)c.assertTrue(false,"actual scout must naturally acquire/see and publish before guard begins: "+state.rig.state());
                        return;
                    }
                    state.original=message.get();state.started=state.now();
                    c.assertTrue(state.rig.scout.getTarget()==state.rig.victim&&state.rig.scout.canSee(state.rig.victim),"first message follows real own-LOS natural acquisition");
                    c.assertTrue(state.original.scout().equals(state.rig.scout.getUuid())&&state.original.target().equals(state.rig.victim.getUuid()),"message has the original real identities");
                    c.assertTrue(state.original.expiresAt()==state.original.observedAt()+60,"expiry is anchored to observation time");
                }
                state.bounds();action.accept(state);
            }catch(Throwable failure){state.close();throw failure;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-hidden-memory",tickLimit=240)
    public void hiddenVictimMovementCannotRewriteCoordinatesOrRenewOriginalExpiry(TestContext c){
        run(c,0,s->{
            if(s.phase++==0)s.sealVictim();
            var current=DroneSquadCoordinator.message(s.rig.wingman);
            if(s.now()<s.original.expiresAt()){
                c.assertTrue(current.isPresent()&&current.get().equals(s.original),"unseen live target cannot rewrite observed XYZ/time or extend lifetime: "+current);
            }else{
                c.assertTrue(current.isEmpty()&&!DroneSquadCoordinator.valid(s.rig.wingman,s.original),"original60tick deadline expires despite repeated reads and a live hidden target");
                c.assertTrue(s.rig.wingman.getTarget()==null,"mailbox never directly assigns the hidden target");s.complete();
            }
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-dead-observer",tickLimit=180)
    public void removedObserverCannotKeepAnInvestigationAlive(TestContext c){
        run(c,512,s->{s.rig.scout.discard();c.assertTrue(DroneSquadCoordinator.message(s.rig.wingman).isEmpty()&&!DroneSquadCoordinator.valid(s.rig.wingman,s.original),"removed scout invalidates the real task before TTL");s.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-dead-target",tickLimit=180)
    public void removedTargetCannotRemainAnInvestigation(TestContext c){
        run(c,1024,s->{s.rig.victim.discard();c.assertTrue(DroneSquadCoordinator.message(s.rig.wingman).isEmpty()&&!DroneSquadCoordinator.valid(s.rig.wingman,s.original),"removed target invalidates the real task before TTL");s.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-current-allegiance",tickLimit=180)
    public void actualScoreboardAllegianceInvalidatesAnEarlierHostileObservation(TestContext c){
        run(c,1536,s->{
            var scoreboard=c.getWorld().getScoreboard();var team=scoreboard.addTeam("sq_"+UUID.randomUUID().toString().substring(0,8));s.cleanup.add(()->scoreboard.removeTeam(team));
            scoreboard.addScoreHolderToTeam(s.rig.wingman.getNameForScoreboard(),team);scoreboard.addScoreHolderToTeam(s.rig.victim.getNameForScoreboard(),team);
            c.assertTrue(s.rig.wingman.isTeammate(s.rig.victim),"fixture uses the actual engine team relation");
            c.assertTrue(DroneSquadCoordinator.message(s.rig.wingman).isEmpty()&&!DroneSquadCoordinator.valid(s.rig.wingman,s.original),"current allegiance rejects a formerly hostile UUID without a hidden-position read");s.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-world-mailbox",tickLimit=180)
    public void anotherServerWorldCannotExposeThisReceiverMessage(TestContext c){
        run(c,2048,s->{
            var other=c.getWorld().getServer().getWorld(World.NETHER);c.assertTrue(other!=null&&other!=c.getWorld(),"a distinct actual server world exists");
            c.assertTrue(DroneSquadCoordinator.message(other,s.rig.wingman.getUuid()).isEmpty(),"receiver UUID lookup in another world cannot expose this world's observation");
            c.assertTrue(DroneSquadCoordinator.message(s.rig.wingman).isPresent(),"world isolation does not discard the legitimate original message");s.complete();
        });
    }
}
