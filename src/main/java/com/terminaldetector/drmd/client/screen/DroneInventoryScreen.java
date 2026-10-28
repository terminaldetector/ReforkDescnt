package com.terminaldetector.drmd.client.screen;
import com.terminaldetector.drmd.inventory.DroneInventoryHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
public final class DroneInventoryScreen extends HandledScreen<DroneInventoryHandler> {
 private com.terminaldetector.drmd.entity.PyroShipEntity preview;
 public DroneInventoryScreen(DroneInventoryHandler h,PlayerInventory inventory,Text title){super(h,inventory,title);backgroundWidth=176;backgroundHeight=212;playerInventoryTitleY=114;}
 protected void init(){super.init();preview=new com.terminaldetector.drmd.entity.PyroShipEntity(com.terminaldetector.drmd.entity.ModEntities.PYRO_SHIP,client.world);}
 protected void drawBackground(DrawContext c,float delta,int mx,int my){
  c.fill(x,y,x+176,y+212,0xee18252e);c.fill(x+4,y+18,x+172,y+79,0xff0d1721);
  var m=c.getMatrices();m.push();m.translate(x+88,y+24,100);m.scale(60,-60,60);
  m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(22));m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(145));
  var dispatcher=client.getEntityRenderDispatcher();var buffers=client.getBufferBuilders().getEntityVertexConsumers();
  dispatcher.setRenderShadows(false);
  try{dispatcher.render(preview,0,0,0,0,0,m,buffers,15728880);buffers.draw();}finally{dispatcher.setRenderShadows(true);m.pop();}
  for(int row=0;row<4;row++)for(int col=0;col<9;col++){
   int sx=x+8+col*18,sy=y+(row==3?184:126+row*18);
   c.fill(sx-1,sy-1,sx+17,sy+17,0xff647987);c.fill(sx,sy,sx+16,sy+16,0xff101c25);
  }
  String[] labels={"ЛКМ","ПКМ","B","Запас","Мотор"};
  for(int i=0;i<5;i++){int sx=x+16+i*36;c.fill(sx-1,y+90,sx+17,y+108,0xff80929c);c.fill(sx,y+91,sx+16,y+107,0xff26353d);c.drawCenteredTextWithShadow(textRenderer,Text.literal(labels[i]),sx+8,y+80,0xa5d8db);}
 }
 protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,8,6,0xe0edf4,false);c.drawText(textRenderer,playerInventoryTitle,8,114,0xa5becb,false);}
 public void render(DrawContext c,int mx,int my,float delta){super.render(c,mx,my,delta);drawMouseoverTooltip(c,mx,my);}
}
