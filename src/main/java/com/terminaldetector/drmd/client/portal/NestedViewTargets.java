package com.terminaldetector.drmd.client.portal;
import com.terminaldetector.drmd.mixin.client.WorldViewTargetsAccess;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.WorldRenderer;
/** Normal opaque rendering inside the private view; retain Fabulous for the outer scene. */
final class NestedViewTargets implements AutoCloseable {
 private final WorldViewTargetsAccess access;
 private final PostEffectProcessor transparency,outlines;
 private final Framebuffer translucent,entities,particles,weather,clouds,outlineTarget;
 NestedViewTargets(WorldRenderer renderer){
  access=(WorldViewTargetsAccess)renderer;
  transparency=access.drmd$transparency();outlines=access.drmd$outlines();
  translucent=access.drmd$translucent();entities=access.drmd$entities();particles=access.drmd$particles();
  weather=access.drmd$weather();clouds=access.drmd$clouds();outlineTarget=access.drmd$outlineTarget();
  access.drmd$transparency(null);access.drmd$outlines(null);access.drmd$translucent(null);access.drmd$entities(null);
  access.drmd$particles(null);access.drmd$weather(null);access.drmd$clouds(null);access.drmd$outlineTarget(null);
 }
 public void close(){
  access.drmd$transparency(transparency);access.drmd$outlines(outlines);access.drmd$translucent(translucent);access.drmd$entities(entities);
  access.drmd$particles(particles);access.drmd$weather(weather);access.drmd$clouds(clouds);access.drmd$outlineTarget(outlineTarget);
 }
}
