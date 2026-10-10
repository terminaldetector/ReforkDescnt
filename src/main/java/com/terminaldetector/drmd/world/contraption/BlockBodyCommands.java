package com.terminaldetector.drmd.world.contraption;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.*;

/** Operator-only laboratory commands; no blocks are removed until the complete selection validates. */
public final class BlockBodyCommands {
    private BlockBodyCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("d6body").requires(source -> source.hasPermissionLevel(2));
            root.then(literal("assemble").then(argument("from", BlockPosArgumentType.blockPos())
                .then(argument("to", BlockPosArgumentType.blockPos()).executes(ctx -> {
                    try {
                        var body = BlockBodyEntity.assemble(ctx.getSource().getWorld(),
                            BlockPosArgumentType.getLoadedBlockPos(ctx, "from"), BlockPosArgumentType.getLoadedBlockPos(ctx, "to"));
                        ctx.getSource().sendFeedback(() -> Text.literal("Body " + body.getUuid() + ": " + body.cells().size() + " blocks"), false);
                        return 1;
                    } catch (IllegalArgumentException | IllegalStateException e) { return error(ctx, e); }
                }))));
            root.then(literal("disassemble").then(argument("body", EntityArgumentType.entity()).executes(ctx -> {
                try { selected(ctx).disassemble(); return 1; }
                catch (IllegalArgumentException | IllegalStateException e) { return error(ctx, e); }
            })));
            for (String action : new String[]{"impulse", "spin"}) {
                root.then(literal(action).then(argument("body", EntityArgumentType.entity())
                    .then(argument("x", DoubleArgumentType.doubleArg(-10000, 10000))
                    .then(argument("y", DoubleArgumentType.doubleArg(-10000, 10000))
                    .then(argument("z", DoubleArgumentType.doubleArg(-10000, 10000)).executes(ctx -> {
                        try {
                            var body = selected(ctx).physics();
                            Vec3 v = new Vec3(DoubleArgumentType.getDouble(ctx,"x"), DoubleArgumentType.getDouble(ctx,"y"), DoubleArgumentType.getDouble(ctx,"z"));
                            if (action.equals("impulse")) body.applyImpulse(v, new Vec3(0,0,0));
                            else body.withAngularMomentum(body.angularMomentum().plus(v));
                            return 1;
                        } catch (IllegalArgumentException e) { return error(ctx,e); }
                    }))))));
            }
            dispatcher.register(root);
        });
    }
    private static BlockBodyEntity selected(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        if (EntityArgumentType.getEntity(ctx,"body") instanceof BlockBodyEntity body) return body;
        throw new IllegalArgumentException("Select a drmd:block_body entity");
    }
    private static int error(CommandContext<ServerCommandSource> ctx, RuntimeException e) {
        ctx.getSource().sendError(Text.literal(e.getMessage())); return 0;
    }
}
