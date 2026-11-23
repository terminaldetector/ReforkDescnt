package com.terminaldetector.drmd.client.planet;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Camera-current radial projection of world-anchored source vertices; GPU geometry stays cached. */
public final class HorizonShader {
    private static ShaderProgram program;
    private HorizonShader() {}
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context->context.register(
                Identifier.of("drmd","horizon_world"),VertexFormats.POSITION_TEXTURE_COLOR,shader->program=shader));
    }
    public static ShaderProgram program() { return program; }
    public static void position(float x,float y,float z,float inner,float reach,boolean volume) {
        if(program==null) return;
        program.getUniform("FieldOriginDelta").set(x,y,z);
        program.getUniform("InnerRadius").set(inner);
        program.getUniform("FieldReach").set(reach);
        program.getUniform("FieldOuterRadius").set(volume ? VolumeHorizonMesh.OUTER_RADIUS : HorizonExtent.outerRadius());
        program.getUniform("VolumeHorizon").set(volume ? 1 : 0);
        com.terminaldetector.drmd.client.presentation.Ps1WorldFog.uniforms(program);
    }

    /** Exactly the same immutable frame mask is supplied to colour and cloud semantic draws. */
    public static void orbitalMask(ShaderProgram shader,float[] values) {
        if(values.length!=HorizonOrbitalHandoff.MAX_MASKS)throw new IllegalArgumentException("orbital shader mask length");
        for(int i=0;i<HorizonOrbitalHandoff.UNIFORM_VECTORS;i++) {
            var uniform=shader.getUniform("OrbitalOpacity["+i+"]");
            if(uniform==null)throw new IllegalStateException("missing orbital shader uniform "+i);
            int at=i*4;uniform.set(values[at],values[at+1],values[at+2],values[at+3]);
        }
    }

    /** Bounded per-object NDC offsets. Reset every draw, including neutral Native/Debug passes. */
    public static void orbitalPixelShift(ShaderProgram shader,float[] shifts) {
        if(shifts.length!=HorizonOrbitalPixelSnap.COMPONENTS)throw new IllegalArgumentException("orbital pixel shift length");
        for(int i=0;i<8;i++) {
            var uniform=shader.getUniform("OrbitalPixelShift["+i+"]");
            if(uniform==null)throw new IllegalStateException("missing orbital pixel shift uniform "+i);
            int at=i*4;uniform.set(shifts[at],shifts[at+1],shifts[at+2],shifts[at+3]);
        }
    }

    /** Use the actual column sky colour for terrain and catalogue objects alike, never a second sky overlay. */
    public static void atmosphere(double eyeY,float skyR,float skyG,float skyB,boolean column) {
        if(program==null)return;
        com.terminaldetector.drmd.client.presentation.Ps1WorldFog.uniforms(program);
    }
}
