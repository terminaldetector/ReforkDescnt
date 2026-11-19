package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.layer.WorldLayer;
import com.terminaldetector.drmd.world.planet.PlanetaryZones;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** Real server-world metadata/HUD integration only; no terrain/navigation acceptance. */
public final class PlanetaryZoneContextGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="orbital-manual-admission")
    public void actualOrbitalCommandsRequireAuthorityAndCannotAdvanceBeforePlacement(TestContext context){
        var world=context.getWorld();var server=world.getServer();var manager=server.getPlayerManager();
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"orbital-author");
        var data=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(server,world,profile,data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);var previous=manager.getOpList().get(profile);
        try{
            manager.onPlayerConnect(connection,player,data);player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            var anchor=context.getAbsolutePos(new net.minecraft.util.math.BlockPos(0,0,0));
            int y=Math.max(world.getBottomY()+48,Math.min(world.getSeaLevel()+32,world.getTopY()-80));
            String command="ungravitycity planorbital 2 "+anchor.getX()+" "+y+" "+anchor.getZ();
            server.getCommandManager().executeWithPrefix(player.getCommandSource(),command);
            context.assertTrue(com.terminaldetector.drmd.world.fixture.city.CityAuthoringSystem.active(player).isEmpty(),"ordinary unprivileged Survival cannot create orbital authoring state");
            manager.getOpList().add(new net.minecraft.server.OperatorEntry(profile,2,false));player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            context.assertTrue(player.hasPermissionLevel(2)&&manager.getPlayer(player.getUuid())==player,"real connected Creative OP2 author, no command-source override");
            var at=new net.minecraft.util.math.BlockPos(anchor.getX(),y,anchor.getZ());
            var before=com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(at);
            server.getCommandManager().executeWithPrefix(player.getCommandSource(),command);
            var session=com.terminaldetector.drmd.world.fixture.city.CityAuthoringSystem.active(player).orElseThrow();
            context.assertTrue(session.inspect().job().isEmpty(),"normal plan command remains inert before actual END-tick admission");
            server.getCommandManager().executeWithPrefix(player.getCommandSource(),"ungravitycity route");
            context.assertTrue(session.inspect().job().isEmpty()&&com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(at).equals(before),"route before placement cannot advance authoring or write geometry");
            server.getCommandManager().executeWithPrefix(player.getCommandSource(),"ungravitycity cancel");
            context.assertTrue(session.inspect().state()==com.terminaldetector.drmd.world.fixture.city.CityRuntimeSession.State.CLOSING,"actual cancel command closes pending work without apply");
        }finally{
            com.terminaldetector.drmd.world.fixture.city.CityAuthoringSystem.active(player).ifPresent(s->s.cancel(player.getUuid()));
            if(previous!=null)manager.getOpList().add(previous);else if(manager.isOperator(profile))manager.removeFromOperators(profile);
            if(manager.getPlayer(player.getUuid())==player)manager.remove(player);player.discard();channel.finishAndReleaseAll();
        }
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualWorldContextResolvesTheSameZoneVocabularyUsedByHud(TestContext context){
        var world=context.getWorld();
        var layout=PlanetaryZones.fit(world.getBottomY(),world.getTopY(),world.getSeaLevel()).orElseThrow();
        for(var band:layout.bands()){
            double y=(long)band.bottom()+((long)band.top()-band.bottom())*.5;
            var display=WorldLayer.display(world,y);
            context.assertTrue(display.insideWorld()&&display.label().equals(band.zone().label),"actual world bounds/sea-level feed the live HUD resolver: "+band.zone());
        }
        context.assertTrue(!WorldLayer.display(world,(double)world.getBottomY()-1).insideWorld(),"real lower dimension bound is not a fabricated Core continuation");
        context.assertTrue(!WorldLayer.display(world,(double)world.getTopY()).insideWorld(),"exclusive actual upper bound is not a fabricated orbit continuation");
        context.complete();
    }
}
