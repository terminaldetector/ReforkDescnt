package com.terminaldetector.drmd.client.screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Retired generators are visible placeholders; they cannot enable old terrain mutation. */
public final class DrmdWorldGenScreen extends Screen {
 private final Screen parent;
 public DrmdWorldGenScreen(Screen parent){super(Text.literal("Мир Ungravity"));this.parent=parent;}
 protected void init(){
  int x=width/2-150,y=Math.max(45,height/2-80);
  for(String title:new String[]{"Advanced — отключено","Психоделическая пустота — заглушка","Бесконечный мегагород — заглушка","Полосы Незера/Энда — отключено"}){
   var b=ButtonWidget.builder(Text.literal(title),button->{}).dimensions(x,y,300,20).build();b.active=false;addDrawableChild(b);y+=24;
  }
  addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(width/2-100,height-28,200,20).build());
 }
 public void render(DrawContext c,int mx,int my,float delta){super.render(c,mx,my,delta);
  c.drawCenteredTextWithShadow(textRenderer,title,width/2,16,0x88ddbb);
  c.drawCenteredTextWithShadow(textRenderer,Text.literal("Cubic Chunks: preset cubic_overworld · Vanilla: обычный мир"),width/2,30,0xcccccc);
 }
 public void close(){client.setScreen(parent);}
}
