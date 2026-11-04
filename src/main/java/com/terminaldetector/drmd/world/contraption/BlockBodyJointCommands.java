package com.terminaldetector.drmd.world.contraption;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.minecraft.command.argument.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Physical operator controls. Creating/removing a link never assigns a hull or passenger pose. */
public final class BlockBodyJointCommands {
    private BlockBodyJointCommands(){}
    public static void add(LiteralArgumentBuilder<ServerCommandSource> root) {
        var joint=literal("joint");
        joint.then(literal("list").executes(c->{var links=BodyJointRegistry.get(c.getSource().getWorld()).links();c.getSource().sendFeedback(()->Text.literal("Joints: "+links.size()),false);for(var l:links.stream().limit(12).toList())c.getSource().sendFeedback(()->Text.literal(l.id()+" "+l.kind()+" "+l.a().body()+" / "+l.b().body()+" speed="+l.speed()),false);return links.size();}));
        joint.then(literal("remove").then(argument("joint",UuidArgumentType.uuid()).executes(c->{if(!registry(c).remove(UuidArgumentType.getUuid(c,"joint")))return error(c,"Joint not found");return 1;})));
        joint.then(literal("motor").then(argument("joint",UuidArgumentType.uuid())
            .then(argument("speed",DoubleArgumentType.doubleArg(-512,512)).then(argument("force",DoubleArgumentType.doubleArg(0,1000000)).executes(c->{
                var l=registry(c).find(UuidArgumentType.getUuid(c,"joint")).orElse(null);if(l==null)return error(c,"Joint not found");
                double speed=DoubleArgumentType.getDouble(c,"speed");if(l.kind()!=BodyJointConstraint.Kind.MOTOR&&l.kind()!=BodyJointConstraint.Kind.SLIDER)return error(c,"Select a MOTOR or SLIDER joint");
                if(l.kind()==BodyJointConstraint.Kind.MOTOR&&Math.abs(speed)>4)return error(c,"Relative angular speed must be between -4 and4rad/s");
                registry(c).configure(l.id(),speed,DoubleArgumentType.getDouble(c,"force"),l.breakForce(),l.breakTorque());return 1;
            })))));
        joint.then(literal("break_load").then(argument("joint",UuidArgumentType.uuid())
            .then(argument("force",DoubleArgumentType.doubleArg(0,1000000000)).then(argument("torque",DoubleArgumentType.doubleArg(0,1000000000)).executes(c->{
                var l=registry(c).find(UuidArgumentType.getUuid(c,"joint")).orElse(null);if(l==null)return error(c,"Joint not found");registry(c).configure(l.id(),l.speed(),l.motorForce(),DoubleArgumentType.getDouble(c,"force"),DoubleArgumentType.getDouble(c,"torque"));return 1;
            })))));
        var create=literal("add");
        for(var kind:BodyJointConstraint.Kind.values())create.then(literal(kind.name().toLowerCase(Locale.ROOT)).then(argument("first",UuidArgumentType.uuid())
            .then(argument("second",UuidArgumentType.uuid()).then(argument("point",Vec3ArgumentType.vec3(false)).executes(c->create(c,kind))))));
        joint.then(create);root.then(joint);
    }
    private static BodyJointRegistry registry(CommandContext<ServerCommandSource> c){return BodyJointRegistry.get(c.getSource().getWorld());}
    private static int create(CommandContext<ServerCommandSource> c,BodyJointConstraint.Kind kind)throws CommandSyntaxException {
        try {
            var a=selected(c,"first");var b=selected(c,"second");
            if(a==b)return error(c,"Select distinct bodies");var point=Vec3ArgumentType.getVec3(c,"point");
            var frameB=ImmPtlQuaternions.hamiltonProduct(b.rotation().inverse(),a.rotation()).normalized();
            var link=new BodyJointLink(UUID.randomUUID(),endpoint(a,point,Quat.IDENTITY),endpoint(b,point,frameB),kind,kind==BodyJointConstraint.Kind.SLIDER?0:Double.NEGATIVE_INFINITY,
                kind==BodyJointConstraint.Kind.SLIDER?3:Double.POSITIVE_INFINITY,kind==BodyJointConstraint.Kind.MOTOR?1:0,kind==BodyJointConstraint.Kind.MOTOR?20:kind==BodyJointConstraint.Kind.SLIDER?60:0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
            registry(c).add(c.getSource().getWorld(),a,b,link);c.getSource().sendFeedback(()->Text.literal("Joint "+link.id()+" "+kind+"; axis=first body localX"),false);return 1;
        }catch(IllegalArgumentException|IllegalStateException e){return error(c,e.getMessage());}
    }
    private static BlockBodyEntity selected(CommandContext<ServerCommandSource> c,String name) {
        var id=UuidArgumentType.getUuid(c,name);
        // The physical index owns loaded hulls even when their vanilla pivot section is not
        // being tracked. Do not make a local-grid operation depend on vanilla entity selectors.
        return BodySpatialIndex.loaded(c.getSource().getWorld()).stream().filter(body->body.getUuid().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Loaded physical body not found: "+id));
    }
    private static BodyJointLink.Endpoint endpoint(BlockBodyEntity body,Vec3d world,Quat frame) {
        var p=body.worldToLocal(world);double e=1e-6;
        var cells=new ArrayList<>(body.localCollisionCandidates(new BodyBounds(new Vec3(p.x-e,p.y-e,p.z-e),new Vec3(p.x+e,p.y+e,p.z+e))));
        cells.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        for(var cell:cells)if(!body.getLocalBlock(cell).isAir()&&p.x>=cell.getX()-e&&p.x<=cell.getX()+1+e&&p.y>=cell.getY()-e&&p.y<=cell.getY()+1+e&&p.z>=cell.getZ()-e&&p.z<=cell.getZ()+1+e)
            return new BodyJointLink.Endpoint(body.getUuid(),cell,new Vec3(MathHelper.clamp(p.x,cell.getX(),cell.getX()+1),MathHelper.clamp(p.y,cell.getY(),cell.getY()+1),MathHelper.clamp(p.z,cell.getZ(),cell.getZ()+1)),frame);
        throw new IllegalArgumentException("The joint point must belong to material in both bodies");
    }
    private static int error(CommandContext<ServerCommandSource> c,String message){c.getSource().sendError(Text.literal(message));return 0;}
}
