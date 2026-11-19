package com.terminaldetector.drmd.world.fixture.city;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.function.Function;

/** Explicit operator fixture commands. Existing world generation is never invoked. */
public final class CityCommands {
    private CityCommands(){}
    static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->{
            var root=CommandManager.literal("ungravitycity").requires(source->source.getEntity() instanceof ServerPlayerEntity p&&CityWorldStorage.eligible(p));
            root.then(CommandManager.literal("plan").then(CommandManager.argument("seed",LongArgumentType.longArg()).then(coordinates(CityManifest.Kind.CITY))));
            root.then(CommandManager.literal("planorbital").then(CommandManager.argument("seed",LongArgumentType.longArg()).then(coordinates(CityManifest.Kind.ORBITAL_PILOT))));
            root.then(CommandManager.literal("inspect").executes(context->execute(context.getSource().getPlayer(),player->{
                    var status=CityAuthoringSystem.inspect(player).inspect();var text=new StringBuilder("City ").append(status.state()).append(": ").append(status.detail());
                    status.manifest().ifPresent(m->text.append("\nAnchor ").append(m.anchor()).append("; template ").append(m.kind()).append("; solids ").append(m.solidCount()).append("/65536; cuts ").append(m.cutCount()).append("/65536; actors0/BE0"));
                    status.closedSnapshot().ifPresent(s->text.append("\nDurable ").append(s.disposition()).append("; cursor ").append(s.cursor()));
                    return text.append("\n").append(status.sterileLimit()).toString();
            })));
            root.then(CommandManager.literal("apply").executes(context->{var p=context.getSource().getPlayer();return control(p,session->session.apply(p.getUuid()));}));
            root.then(CommandManager.literal("pause").executes(context->{var p=context.getSource().getPlayer();return control(p,session->session.pause(p.getUuid()));}));
            root.then(CommandManager.literal("resume").executes(context->{var p=context.getSource().getPlayer();return control(p,session->session.resume(p.getUuid()));}));
            root.then(CommandManager.literal("cancel").executes(context->{var p=context.getSource().getPlayer();return control(p,session->session.cancel(p.getUuid()));}));
            root.then(CommandManager.literal("route").executes(context->execute(context.getSource().getPlayer(),p->
                CityAuthoringSystem.active(p).orElseThrow(()->new IllegalStateException("No current orbital pilot")).route(p.getUuid()))));
            dispatcher.register(root);
        });
    }
    private static RequiredArgumentBuilder<ServerCommandSource,Integer> coordinates(CityManifest.Kind kind){
        return CommandManager.argument("x",IntegerArgumentType.integer()).then(CommandManager.argument("y",IntegerArgumentType.integer())
            .then(CommandManager.argument("z",IntegerArgumentType.integer()).executes(context->execute(context.getSource().getPlayer(),p->{
                var anchor=new CityBlueprint.Point(IntegerArgumentType.getInteger(context,"x"),IntegerArgumentType.getInteger(context,"y"),IntegerArgumentType.getInteger(context,"z"));
                CityAuthoringSystem.plan(p,LongArgumentType.getLong(context,"seed"),anchor,kind);
                return kind==CityManifest.Kind.CITY?"Preparing one inert fixed-slot city plan; inspect before apply.":"Preparing inert original orbital ring/Citadel pilot; inspect then apply explicitly. No world writes yet.";
            }))));
    }
    private static int control(ServerPlayerEntity player,Function<CityRuntimeSession,Boolean> operation){
        return execute(player,p->{
            var session=CityAuthoringSystem.active(p).orElseThrow(()->new IllegalStateException("No current session; inspect existing data first"));
            boolean accepted=operation.apply(session);var status=session.inspect();return (accepted?"Accepted: ":"Not active or still saving: ")+status.state()+"; "+status.detail()+"\n"+status.sterileLimit();
        });
    }
    private static int execute(ServerPlayerEntity player,Function<ServerPlayerEntity,String> action){
        try{if(!CityWorldStorage.eligible(player))throw new IllegalArgumentException("connected Creative operator permission2 required");player.sendMessage(Text.literal(action.apply(player)),false);return 1;}
        catch(RuntimeException refused){player.sendMessage(Text.literal("City paused/refused: "+refused.getMessage()+"\n"+CityWorldStorage.STERILE_LIMIT),false);return 0;}
    }
}
