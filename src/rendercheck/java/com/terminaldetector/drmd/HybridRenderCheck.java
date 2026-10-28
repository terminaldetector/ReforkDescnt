package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.render.terrain.HybridTerrainModel;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import com.terminaldetector.drmd.world.geometry.HybridTerrainCommands;
import com.terminaldetector.drmd.world.micro.CarvedBlock;
import com.terminaldetector.drmd.world.micro.CarvedBlockEntity;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs only in the isolated rendercheck source set; never included in the playable mod. */
public final class HybridRenderCheck implements ClientModInitializer {
    private static final BlockPos DAMAGED=new BlockPos(20,203,18);
    private static final Box GRAVITY_TRACKING=new Box(32,180,-80,82,240,90);
    private static final Box DRONE_SWARM_TRACKING=new Box(20,180,-50,90,240,60);
    private static final int LIVE_SWARM_SIZE=128;
    private volatile boolean ready;
    private volatile boolean collapseReady;
    private volatile boolean gravityReady=true;
    private volatile boolean droneSwarmReady=true;
    private volatile double swarmLaunchSpeed;
    private volatile double railLaunchSpeed;
    private volatile UUID shiftBodyId;
    private volatile Vec3d shiftStartPosition=Vec3d.ZERO;
    private volatile Vec3d shiftDirection=Vec3d.ZERO;
    private volatile List<UUID> gravityBodyIds=List.of();
    private volatile List<UUID> droneSwarmIds=List.of();
    private volatile int swarmIndexedDrones;
    private volatile int swarmMaxCandidates;
    private volatile int swarmMovingDrones;
    private volatile int swarmStartTick;
    private int lastSceneServerTick=-1;
    private volatile boolean gravityArenaBuilt;
    private boolean setupQueued;
    private int ticks, sceneTicks;
    private int collapseBeforePixels;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        ++ticks;
        if (ticks%200==0) System.out.println("DRMD rendercheck: screen="+
                (client.currentScreen==null?"none":client.currentScreen.getClass().getName()+": "+client.currentScreen.getTitle().getString())+
                ", world="+(client.world!=null)+", ready="+ready+", sceneTicks="+sceneTicks);
        if (ticks>4800) {
            capture(client,"hybrid-timeout.png");
            throw new IllegalStateException("Render check timed out; screen="+
                    (client.currentScreen==null?"none":client.currentScreen.getClass().getName())+", sceneTicks="+sceneTicks);
        }
        // GameTest saves use the experimental registry lifecycle. Only this disposable fixture
        // may acknowledge its load warning; this source set is absent from the playable jar.
        if (client.currentScreen instanceof BackupPromptScreen prompt) {
            if (!client.runDirectory.toPath().getFileName().toString().equals("rendercheck"))
                throw new IllegalStateException("Render check must run in its disposable directory");
            for (var child:prompt.children()) if (child instanceof ButtonWidget button &&
                    button.getMessage().getString().equals("I know what I'm doing!")) {
                System.out.println("DRMD rendercheck: loading disposable experimental fixture");
                button.onPress();
                return;
            }
            throw new IllegalStateException("Experimental fixture prompt has no expected continue button");
        }
        if (client.world==null || client.player==null || client.getServer()==null) return;
        DescentClient.markUserFlightChoice();
        DescentPlayerData.get(client.player).setEnabled(false);
        // Saved flight sync can leave the independent camera state enabled after the
        // fixture disables PlayerData; pin both states before the next world render.
        com.terminaldetector.drmd.client.DescentClientState.enabled=false;
        DescentConfig.cockpit=false;DescentConfig.hud=false;DescentConfig.weaponView=false;
        DescentConfig.planetFloor=false;DescentConfig.levelSky=false;DescentConfig.skyUfoVirtualHull=false;
        client.options.hudHidden=true;client.options.pauseOnLostFocus=false;
        if (!setupQueued) {
            setupQueued=true;
            var server=client.getServer();var uuid=client.player.getUuid();
            server.execute(() -> {
                var player=server.getPlayerManager().getPlayer(uuid);
                if (player==null) throw new IllegalStateException("Missing render-check player");
                var world=server.getOverworld();setup(world);
                player.changeGameMode(GameMode.SPECTATOR);
                DescentPlayerData.get(player).setEnabled(false);
                player.teleport(world,14,209,29,180,28);
                ready=true;
            });
            return;
        }
        if (!ready || client.currentScreen!=null) return;
		positionCamera(client);
        if (!(client.getBlockRenderManager().getModel(ModWorldBlocks.CARVED.getDefaultState()) instanceof HybridTerrainModel))
            throw new IllegalStateException("The carved blockstate did not receive the hybrid baked model");
        if(sceneTicks>=260 && !collapseReady)return;
        if(sceneTicks>=800 && !gravityReady)return;
        if(sceneTicks>=1055 && !droneSwarmReady)return;
        // Rendering can run ahead of the integrated server under software GL. Give
        // the actual AI a fixed server-time interval before measuring its motion.
        if(sceneTicks==1084 && client.getServer().getTicks()-swarmStartTick<30)return;
        int serverTick=client.getServer().getTicks();
        if(serverTick==lastSceneServerTick)return;
        lastSceneServerTick=serverTick;
        ++sceneTicks;
        if (sceneTicks==160) {
            capture(client,"hybrid-before.png");
            client.getServer().execute(() -> {
                var world=client.getServer().getOverworld();
                var state=world.getBlockState(DAMAGED);
                if (!(state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock) || world.getBlockEntity(DAMAGED)!=null)
                    throw new IllegalStateException("Intact organic terrain must be palette-backed");
                long mask=MicroGrid.carve(MicroGrid.FULL,.5,.5,.95,.5);
                var be=CarvedBlock.replace(world,DAMAGED,state,mask);
                if(be==null || !be.organic())throw new IllegalStateException("Organic damage transition lost mode");
                world.setBlockState(DAMAGED.east(),Blocks.BRICKS.getDefaultState());
            });
        }
        if (sceneTicks==240) {
            if (!(client.world.getBlockEntity(DAMAGED) instanceof CarvedBlockEntity be) || be.mask()==MicroGrid.FULL || !be.organic())
                throw new IllegalStateException("Damage/mode did not sync to the render-check client");
            capture(client,"hybrid-after.png");
            try {Files.writeString(client.runDirectory.toPath().resolve("rendercheck-ok.txt"),"Hybrid model loaded; organic mode and damage synced; before/after scene captured.\n");}
            catch (java.io.IOException e) {throw new IllegalStateException(e);}
        }
        if(sceneTicks==260)client.getServer().execute(() -> {
            var world=client.getServer().getOverworld();
            for(BlockPos p:BlockPos.iterate(new BlockPos(1,200,4),new BlockPos(35,216,31))) {
				BlockState desired=p.getY()==200?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState();
				if(!world.getBlockState(p).equals(desired))world.setBlockState(p,desired);
			}
            for(int y=201;y<=207;y++)world.setBlockState(new BlockPos(10,y,14),Blocks.OAK_LOG.getDefaultState());
            for(BlockPos p:BlockPos.iterate(new BlockPos(8,205,12),new BlockPos(12,209,16)))
                if(world.getBlockState(p).isAir() && Math.abs(p.getX()-10)+Math.abs(p.getZ()-14)+Math.abs(p.getY()-207)<=4)
                    world.setBlockState(p,Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.state.property.Properties.PERSISTENT,true));
            for(int y=201;y<=211;y++)world.setBlockState(new BlockPos(25,y,14),Blocks.BRICKS.getDefaultState());
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.teleport(world,17,211,37,180,24);collapseReady=true;
        });
        if(sceneTicks==340) {
            if(!collapseReady)throw new IllegalStateException("Collapse fixture setup incomplete");
            collapseBeforePixels=capture(client,"collapse-before.png");
            client.getServer().execute(() -> {
                var world=client.getServer().getOverworld();var owner=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(int x:new int[]{10,25}) {
                    var rocket=new com.terminaldetector.drmd.entity.ProjectileEntity(com.terminaldetector.drmd.entity.ModEntities.PROJECTILE,world);
                    rocket.setOwner(owner);rocket.setDamageClass(com.terminaldetector.drmd.weapon.core.DamageClass.EXPLOSIVE);
                    rocket.setMeshKind(com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET);rocket.setVisualScale(1);
                    rocket.setDirectDamage(100);rocket.setSplashDamage(35);rocket.setSplashRadius(2);rocket.setWorldBlast(true);
                    rocket.setPosition(x+.5,201.5,29);rocket.setVelocity(0,0,-.7);world.spawnEntity(rocket);
                }
            });
        }
        if(sceneTicks==354)capture(client,"collapse-flight.png");
        if(sceneTicks>=340 && sceneTicks<=680 && sceneTicks%5==0)capture(client,String.format("collapse-frame-%04d.png",sceneTicks));
        if(sceneTicks==680) {
            var bodies=client.world.getEntitiesByClass(com.terminaldetector.drmd.world.contraption.BlockBodyEntity.class,
                new net.minecraft.util.math.Box(1,200,4,35,216,31),b -> b.physicsGravity());
            if(bodies.size()!=2 || bodies.stream().anyMatch(b -> Math.abs(b.rotation().x())<.35))
                throw new IllegalStateException("Rocket-cut tree/column did not physically tip on client: "+bodies.size());
            int afterPixels=capture(client,"collapse-after.png");
            try {Files.writeString(client.runDirectory.toPath().resolve("collapse-view-ok.json"),
                "{\"beforeArenaPixels\":"+collapseBeforePixels+",\"afterArenaPixels\":"+afterPixels+"}\n");}
            catch(java.io.IOException e){throw new IllegalStateException(e);}
			try {Files.writeString(client.runDirectory.toPath().resolve("collapse-ok.txt"),"Real rocket impacts detached tree and column; both gravity bodies tipped; flight and before/after frames captured.\n");}
            catch(java.io.IOException e){throw new IllegalStateException(e);}
			client.getServer().execute(() -> {
				var world=client.getServer().getOverworld();
				var drone=com.terminaldetector.drmd.entity.ModEntities.DRONE.create(world);
				if(drone==null)throw new IllegalStateException("Cannot create render-check drone");
				drone.setPosition(17.5,206,20.5);drone.setVelocity(.12,.02,.03);drone.setHealth(1);
				world.spawnEntity(drone);drone.damage(drone.getDamageSources().generic(),1000);
			});
		}
		if(sceneTicks==692)capture(client,"ragdoll-flight.png");
		if(sceneTicks==800) {
			var wrecks=client.world.getEntitiesByClass(com.terminaldetector.drmd.entity.RagdollEntity.class,
					new Box(10,200,12,25,212,28),net.minecraft.entity.Entity::isAlive);
			if(wrecks.size()!=1 || wrecks.getFirst().currentRenderParts().size()!=5)
				throw new IllegalStateException("Drone death did not sync one five-part ragdoll to client: "+wrecks.size());
			boolean articulated=wrecks.getFirst().currentRenderParts().stream().skip(1).anyMatch(part ->
					Math.abs(part.rotation().x())+Math.abs(part.rotation().y())+Math.abs(part.rotation().z())>.03);
			if(!articulated)throw new IllegalStateException("Ragdoll parts reached client but never articulated");
			capture(client,"ragdoll-after.png");
			try {Files.writeString(client.runDirectory.toPath().resolve("ragdoll-ok.txt"),
					"Drone death spawned one networked five-part ragdoll; articulated renderer survived flight and terrain contact.\n");}
			catch(java.io.IOException e){throw new IllegalStateException(e);}
			queueSwarmScene(client);
        }
		if(sceneTicks==840) {
			var bodies=gravityBodies(client);
			if(bodies.size()!=6 || bodies.stream().anyMatch(body -> body.cells().size()!=1))
				throw new IllegalStateException("Swarm hold did not sync six complete block bodies: "+bodies.size());
			capture(client,"gravity-swarm-held.png");
		}
		if(sceneTicks==850)queueSwarmLaunch(client);
		if(sceneTicks==856) {
			var bodies=gravityBodies(client);
			long moving=bodies.stream().filter(body -> body.physicsVelocity().length()>18).count();
			if(bodies.size()!=6 || moving!=6)
				throw new IllegalStateException("Swarm volley did not launch all six bodies: "+moving+"/"+bodies.size()+
						" visible="+gravityBodySummary(client));
			capture(client,"gravity-swarm-flight.png");
		}
		if(sceneTicks==880)queueRailScene(client);
		if(sceneTicks==930) {
			var bodies=gravityBodies(client);
			double alignment=bodies.size()==1?Math.abs(bodies.getFirst().physicsLongAxis().orElse(Vec3d.ZERO)
					.dotProduct(new Vec3d(0,0,-1))):0;
			if(bodies.size()!=1 || bodies.getFirst().cells().size()!=7 || alignment<.65)
				throw new IllegalStateException("Gravity rail did not align its long body with the launch corridor");
			capture(client,"gravity-rail-held.png");
		}
		if(sceneTicks==940)queueRailLaunch(client);
		if(sceneTicks==946) {
			var bodies=gravityBodies(client);
			if(bodies.size()!=1 || railLaunchSpeed<swarmLaunchSpeed+8
					|| bodies.getFirst().physicsVelocity().length()<swarmLaunchSpeed+8)
				throw new IllegalStateException("Heavy gravity rail was not faster than the swarm volley; visible="+
						gravityBodySummary(client));
			capture(client,"gravity-rail-flight.png");
		}
		if(sceneTicks==970)queueShiftScene(client);
		if(sceneTicks==1010) {
			var bodies=gravityBodies(client);
			if(bodies.size()!=1 || bodies.getFirst().cells().size()!=2)
				throw new IllegalStateException("Gravity shifter did not hold its physical target");
			capture(client,"gravity-shift-held.png");
		}
		if(sceneTicks==1020)queueShiftLaunch(client);
		if(sceneTicks==1026)capture(client,"gravity-shift-flight.png");
		if(sceneTicks==1045)queueShiftVerification(client);
		if(sceneTicks==1055) {
			try {Files.writeString(client.runDirectory.toPath().resolve("gravity-weapons-ok.txt"),
					"Swarm held and launched six bodies; rail aligned and outpaced the volley; shifter attached a moving horizontal gravity field.\n");}
			catch(java.io.IOException e){throw new IllegalStateException(e);}
			queueDroneSwarmScene(client);
		}
		if(sceneTicks==1065) {
			var drones=clientDroneSwarm(client);
			if(drones.size()!=LIVE_SWARM_SIZE)
				throw new IllegalStateException("Dense swarm did not synchronize to the client: "+drones.size());
			capture(client,"drone-swarm-dense.png");
		}
		if(sceneTicks==1080)capture(client,"drone-swarm-flight.png");
		if(sceneTicks==1085)queueDroneSwarmVerification(client);
		if(sceneTicks==1090) {
			var drones=clientDroneSwarm(client);
			if(drones.size()!=LIVE_SWARM_SIZE)
				throw new IllegalStateException("Live swarm lost client entities before verification: "+drones.size());
			try {Files.writeString(client.runDirectory.toPath().resolve("swarm-ai-ok.txt"),
					"Live swarm synchronized "+drones.size()+" drones; indexed="+swarmIndexedDrones+
					"; moving="+swarmMovingDrones+"; maximum candidates/query="+swarmMaxCandidates+".\n");}
			catch(java.io.IOException e){throw new IllegalStateException(e);}
			client.scheduleStop();
		}
    }

	private void positionCamera(MinecraftClient client) {
		if(sceneTicks>=720 && sceneTicks<800) {
			var wrecks=client.world.getEntitiesByClass(com.terminaldetector.drmd.entity.RagdollEntity.class,
					new Box(8,198,8,28,216,32),net.minecraft.entity.Entity::isAlive);
			if(!wrecks.isEmpty()) {
				var wreck=wrecks.getFirst();
				client.player.setPosition(wreck.getX(),wreck.getY()+1,wreck.getZ()+5.5);
				client.player.setVelocity(Vec3d.ZERO);client.player.setYaw(180);client.player.setPitch(20);
				return;
			}
		}
		if(sceneTicks>=800) {
			client.player.setPosition(56.5,206,34);client.player.setVelocity(Vec3d.ZERO);
			client.player.setYaw(180);client.player.setPitch(0);return;
		}
		client.player.setPosition(sceneTicks>=680?17:sceneTicks>=260?17:14,
				sceneTicks>=680?205:sceneTicks>=260?211:209,
				sceneTicks>=680?30:sceneTicks>=260?37:29);client.player.setVelocity(Vec3d.ZERO);
		client.player.setYaw(180);client.player.setPitch(sceneTicks>=680?12:sceneTicks>=260?24:28);
	}

	private void queueSwarmScene(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing gravity render-check player");
			prepareGravityArena(world,player);
			BlockState[] palette={Blocks.IRON_BLOCK.getDefaultState(),Blocks.GOLD_BLOCK.getDefaultState(),
					Blocks.DIAMOND_BLOCK.getDefaultState(),Blocks.GLASS.getDefaultState(),
					Blocks.BRICKS.getDefaultState(),Blocks.OBSIDIAN.getDefaultState()};
			List<BlockBodyEntity> bodies=new ArrayList<>();
			for(int index=0;index<palette.length;index++) {
				BlockPos pos=new BlockPos(51+index*2,205,23+(index&1));
				bodies.add(assembleBody(world,pos,pos,palette[index]));
			}
			gravityBodyIds=bodies.stream().map(BlockBodyEntity::getUuid).toList();
			int captured=GravyPhysics.tryGrab(player,GravyPhysics.Mode.SWARM,bodies);
			if(captured!=6)throw new IllegalStateException("Swarm captured "+captured+" of six bodies");
			gravityReady=true;
		});
	}

	private void queueSwarmLaunch(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing swarm launch player");
			var bodies=serverGravityBodies(world);
			if(GravyPhysics.fling(player)!=6)throw new IllegalStateException("Swarm volley lost a held body");
			swarmLaunchSpeed=bodies.stream().mapToDouble(body -> body.physicsVelocity().length()).min().orElseThrow();
			if(swarmLaunchSpeed<24)throw new IllegalStateException("Swarm launch speed collapsed to "+swarmLaunchSpeed);
			gravityReady=true;
		});
	}

	private void queueRailScene(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing rail render-check player");
			prepareGravityArena(world,player);
			var body=assembleBody(world,new BlockPos(53,205,23),new BlockPos(59,205,23),Blocks.BRICKS.getDefaultState());
			gravityBodyIds=List.of(body.getUuid());
			if(GravyPhysics.tryGrab(player,GravyPhysics.Mode.RAIL,List.of(body))!=1)
				throw new IllegalStateException("Gravity rail could not capture its heavy body");
			gravityReady=true;
		});
	}

	private void queueRailLaunch(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing rail launch player");
			var bodies=serverGravityBodies(world);
			if(bodies.size()!=1)throw new IllegalStateException("Gravity rail scene has "+bodies.size()+" bodies");
			Vec3d aim=WeaponCore.aimDir(player).normalize();
			if(Math.abs(bodies.getFirst().physicsLongAxis().orElse(Vec3d.ZERO).dotProduct(aim))<.65)
				throw new IllegalStateException("Server rail alignment did not converge");
			if(GravyPhysics.fling(player)!=1)throw new IllegalStateException("Gravity rail lost its held body");
			railLaunchSpeed=bodies.getFirst().physicsVelocity().length();
			if(railLaunchSpeed<swarmLaunchSpeed+8)
				throw new IllegalStateException("Rail speed "+railLaunchSpeed+" did not beat swarm "+swarmLaunchSpeed);
			gravityReady=true;
		});
	}

	private void queueShiftScene(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing shifter render-check player");
			prepareGravityArena(world,player);
			var body=assembleBody(world,new BlockPos(56,205,23),new BlockPos(56,206,23),Blocks.GOLD_BLOCK.getDefaultState());
			gravityBodyIds=List.of(body.getUuid());
			if(GravyPhysics.tryGrab(player,GravyPhysics.Mode.SHIFT,List.of(body))!=1)
				throw new IllegalStateException("Gravity shifter could not capture its body");
			gravityReady=true;
		});
	}

	private void queueShiftLaunch(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing shifter launch player");
			var bodies=serverGravityBodies(world);
			if(bodies.size()!=1 || GravyPhysics.fling(player)!=1)
				throw new IllegalStateException("Gravity shifter lost its physical target");
			var body=bodies.getFirst();Vec3d aim=WeaponCore.aimDir(player).normalize();
			if(!body.physicsGravity())throw new IllegalStateException("Shifter launch did not enable physical gravity");
			GravityFields.Sample field=GravityFields.sample(world,body.getPos());
			if(field==null || field.downDir().normalize().dotProduct(aim)<.98)
				throw new IllegalStateException("Shifter did not attach launch-direction gravity");
			shiftBodyId=body.getUuid();shiftStartPosition=body.getPos();shiftDirection=aim;gravityReady=true;
		});
	}

	private void queueShiftVerification(MinecraftClient client) {
		gravityReady=false;var server=client.getServer();
		server.execute(() -> {
			var world=server.getOverworld();var entity=world.getEntity(shiftBodyId);
			if(!(entity instanceof BlockBodyEntity body))throw new IllegalStateException("Shifter body unloaded before verification");
			GravityFields.Sample field=GravityFields.sample(world,body.getPos());
			if(field==null || field.downDir().normalize().dotProduct(shiftDirection)<.98)
				throw new IllegalStateException("Moving shifter field no longer followed its body");
			Vec3d travel=body.getPos().subtract(shiftStartPosition);
			if(Math.abs(travel.y)>.75 || travel.dotProduct(shiftDirection)<8)
				throw new IllegalStateException("Horizontal gravity throw drifted vertically or failed to travel");
			gravityReady=true;
		});
	}

	private void queueDroneSwarmScene(MinecraftClient client) {
		droneSwarmReady=false;var server=client.getServer();var playerId=client.player.getUuid();
		server.execute(() -> {
			var player=server.getPlayerManager().getPlayer(playerId);var world=server.getOverworld();
			if(player==null)throw new IllegalStateException("Missing drone swarm render-check player");
			prepareGravityArena(world,player);server.setDifficulty(Difficulty.NORMAL,true);
			swarmIndexedDrones=0;swarmMaxCandidates=0;swarmMovingDrones=0;
			for(var entity:world.iterateEntities())if(entity instanceof DroneEntity)entity.discard();
			var target=net.minecraft.entity.EntityType.COW.create(world);
			if(target==null)throw new IllegalStateException("Cannot create drone swarm target");
			target.setPosition(56.5,207,31);target.setNoGravity(true);target.setInvulnerable(true);
			target.setInvisible(true);target.setAiDisabled(true);target.setPersistent();world.spawnEntity(target);

			List<DroneEntity> drones=new ArrayList<>();
			for(int index=0;index<LIVE_SWARM_SIZE;index++) {
				var drone=ModEntities.DRONE.create(world);
				if(drone==null)throw new IllegalStateException("Cannot create live swarm drone "+index);
				double x=56.5+((index&7)-3.5)*.7;
				double y=207+(((index>>3)&3)-1.5)*.7;
				double z=23+(((index>>5)&3)-1.5)*.7;
				drone.refreshPositionAndAngles(x,y,z,180,0);drone.applyRole(AiRole.SUPPORT);drone.setPersistent();
				world.spawnEntity(drone);drone.setTarget(target);drones.add(drone);
			}
			droneSwarmIds=drones.stream().map(DroneEntity::getUuid).toList();DroneSwarmAi.clear();
			long examined=0;int maxCandidates=0;long snapshotTick=-1;
			for(DroneEntity drone:drones) {
				var stats=DroneSwarmAi.queryStats(drone);
				if(snapshotTick<0)snapshotTick=stats.snapshotTick();
				if(stats.snapshotTick()!=snapshotTick)
					throw new IllegalStateException("Live swarm rebuilt its snapshot inside one pass");
				if(stats.neighbours()!=DroneSwarmAi.MAX_BOID_NEIGHBOURS)
					throw new IllegalStateException("Dense live swarm returned only "+stats.neighbours()+" neighbours");
				examined+=stats.examinedCandidates();maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());
				swarmIndexedDrones=Math.max(swarmIndexedDrones,stats.indexedDrones());
			}
			if(swarmIndexedDrones<LIVE_SWARM_SIZE || maxCandidates>DroneSwarmAi.MAX_BOID_CANDIDATES
					|| examined>(long)LIVE_SWARM_SIZE*DroneSwarmAi.MAX_BOID_CANDIDATES)
				throw new IllegalStateException("Live swarm escaped its bounded spatial pass");
			swarmStartTick=server.getTicks();swarmMaxCandidates=maxCandidates;droneSwarmReady=true;
		});
	}

	private void queueDroneSwarmVerification(MinecraftClient client) {
		droneSwarmReady=false;var server=client.getServer();
		server.execute(() -> {
			var world=server.getOverworld();List<DroneEntity> drones=new ArrayList<>();
			for(UUID id:droneSwarmIds) {
				var entity=world.getEntity(id);
				if(entity instanceof DroneEntity drone && drone.isAlive())drones.add(drone);
			}
			if(drones.size()!=LIVE_SWARM_SIZE)
				throw new IllegalStateException("Server lost live swarm drones: "+drones.size());
			swarmMovingDrones=(int)drones.stream().filter(drone -> drone.getPhaseTimer()>.5f
					&& drone.getVelocity().lengthSquared()>.0025).count();
			if(swarmMovingDrones<LIVE_SWARM_SIZE*3/4)
				throw new IllegalStateException("Live swarm AI failed to move enough drones: "+swarmMovingDrones);
			long examined=0;int maxCandidates=0;
			for(DroneEntity drone:drones) {
				var stats=DroneSwarmAi.queryStats(drone);examined+=stats.examinedCandidates();
				maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());
				swarmIndexedDrones=Math.max(swarmIndexedDrones,stats.indexedDrones());
			}
			if(maxCandidates>DroneSwarmAi.MAX_BOID_CANDIDATES
					|| examined>(long)LIVE_SWARM_SIZE*DroneSwarmAi.MAX_BOID_CANDIDATES)
				throw new IllegalStateException("Moving swarm escaped its bounded spatial pass");
			swarmMaxCandidates=Math.max(swarmMaxCandidates,maxCandidates);droneSwarmReady=true;
		});
	}

	private void prepareGravityArena(ServerWorld world,net.minecraft.server.network.ServerPlayerEntity player) {
		GravyPhysics.release(player);TransientGravityFields.clear();
		serverGravityBodies(world).forEach(BlockBodyEntity::discard);gravityBodyIds=List.of();
		// The surrounding GameTest fixture is empty at this altitude. Rewriting sixteen thousand air
		// blocks for every mode made the integrated server build a huge catch-up backlog, so launched
		// bodies could cross the whole arena between two client ticks. Build the corridor floor once.
		if(!gravityArenaBuilt) {
			for(BlockPos pos:BlockPos.iterate(new BlockPos(48,200,-20),new BlockPos(65,200,40)))
				world.setBlockState(pos,Blocks.OBSIDIAN.getDefaultState());
			gravityArenaBuilt=true;
		}
		player.teleport(world,56.5,206,34,180,0);
		Vec3d forward=player.getRotationVec(1).normalize();
		DescentPlayerData.get(player).setShipAttitude((float)forward.x,(float)forward.y,(float)forward.z,0,1,0);
	}

	private static BlockBodyEntity assembleBody(ServerWorld world,BlockPos from,BlockPos to,BlockState state) {
		for(BlockPos pos:BlockPos.iterate(from,to))world.setBlockState(pos,state);
		return BlockBodyEntity.assemble(world,from,to);
	}

	private List<BlockBodyEntity> serverGravityBodies(ServerWorld world) {
		List<BlockBodyEntity> bodies=new ArrayList<>();
		for(UUID id:gravityBodyIds) {
			var entity=world.getEntity(id);
			if(entity instanceof BlockBodyEntity body && body.isAlive())bodies.add(body);
		}
		return bodies;
	}

	private List<BlockBodyEntity> gravityBodies(MinecraftClient client) {
		return client.world.getEntitiesByClass(BlockBodyEntity.class,GRAVITY_TRACKING,
				body -> body.isAlive() && gravityBodyIds.contains(body.getUuid()));
	}

	private List<DroneEntity> clientDroneSwarm(MinecraftClient client) {
		return client.world.getEntitiesByClass(DroneEntity.class,DRONE_SWARM_TRACKING,
				drone -> drone.isAlive() && droneSwarmIds.contains(drone.getUuid()));
	}

	private String gravityBodySummary(MinecraftClient client) {
		return client.world.getEntitiesByClass(BlockBodyEntity.class,GRAVITY_TRACKING,BlockBodyEntity::isAlive)
				.stream().map(body -> body.getUuid()+"@"+body.getBlockPos()+" v="+
						String.format(java.util.Locale.ROOT,"%.1f",body.physicsVelocity().length()))
				.collect(java.util.stream.Collectors.joining(","));
	}

    private static void setup(ServerWorld world) {
        world.setTimeOfDay(6000);
        for(BlockPos p:BlockPos.iterate(new BlockPos(2,200,5),new BlockPos(27,211,24)))
            world.setBlockState(p,p.getY()==200?Blocks.IRON_BLOCK.getDefaultState():Blocks.AIR.getDefaultState());
        for(int centre:new int[]{8,20}) for(int x=centre-4;x<=centre+4;x++) for(int z=9;z<=17;z++) {
            int height=Math.max(1,5-Math.max(Math.abs(x-centre),Math.abs(z-13)));
            for(int y=201;y<201+height;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.STONE.getDefaultState());
        }
        world.setBlockState(DAMAGED,Blocks.STONE.getDefaultState());
        HybridTerrainCommands.apply(world,new BlockPos(16,201,9),new BlockPos(24,206,18),true);
        for(int y=201;y<=204;y++) world.setBlockState(new BlockPos(25,y,14),Blocks.BRICKS.getDefaultState());
        var rigid=new BlockPos(10,202,19);
        world.setBlockState(rigid,Blocks.OAK_PLANKS.getDefaultState());
        CarvedBlock.replace(world,rigid,Blocks.OAK_PLANKS.getDefaultState(),MicroGrid.carve(-1,.9,.8,.9,.45));
        var glass=rigid.east(2);world.setBlockState(glass,Blocks.RED_STAINED_GLASS.getDefaultState());
        CarvedBlock.replace(world,glass,Blocks.RED_STAINED_GLASS.getDefaultState(),MicroGrid.carve(-1,.9,.8,.9,.45));
    }
    private static int capture(MinecraftClient client,String name) {
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var directory=client.runDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
            image.writeTo(directory.resolve(name));
            int arenaPixels=0;
            if(name.equals("collapse-before.png") || name.equals("collapse-after.png")) {
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int color=image.getColor(x,y);
                    if((color&255)<130 && ((color>>>8)&255)<130 && ((color>>>16)&255)<130)arenaPixels++;
                }
                if(arenaPixels<2000)throw new IllegalStateException("Collapse frame shows no arena: "+name+" pixels="+arenaPixels);
            }
            return arenaPixels;
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot save render verification",e);}
    }
}
