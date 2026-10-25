package com.terminaldetector.drmd;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import java.util.*;

/** Test-only real item-model atlas; sprite fingerprints are taken from actual client pixels. */
public final class AuditInventoryScreen extends Screen {
    public static final String[] IDS={"bomb_cluster","bomb_heavy_cluster","bomb_cluster_laser","bomb_cluster_virus_nether","bomb_cluster_virus_sculk",
            "energy_cell","energy_orb_pickup","targeting_core","shield_orb","portal_gun","portal_stabilizer",
            "weapon_d6_concussion","weapon_d6_flash","weapon_d6_flak","weapon_d6_helix","weapon_d6_fusion","weapon_d6_omega",
            "weapon_d6_homing","weapon_d6_guided","weapon_d6_mega_missile","weapon_d6_earthshaker","weapon_d6_plasma","weapon_d6_phoenix",
            "weapon_d6_smart_missile","weapon_d6_mercury","weapon_d6_vulcan","weapon_d6_gauss",
            "spawn_egg_assault","spawn_egg_interceptor","spawn_egg_artillery","spawn_egg_support","spawn_egg_heavy_elite","spawn_egg_mg",
            "spawn_egg_laser","spawn_egg_rpg","spawn_egg_heavy","spawn_egg_seeker","spawn_egg_tripod","spawn_egg_scanner","spawn_egg_spider_turret","spawn_egg_oblivion_seeker"};
    public AuditInventoryScreen() {super(Text.literal("Audit: inventory model comparison"));}
    public int iconX(int index) {return (width-8*68)/2+(index%8)*68+12;}
    public int iconY(int index) {return 36+(index/8)*48;}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.fill(0,0,width,height,0xff101820);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xffffff);
        for(int i=0;i<IDS.length;i++) {
            var item=Registries.ITEM.get(Identifier.of("drmd",IDS[i]));
            if(item==Items.AIR)throw new IllegalStateException("Missing inventory item: "+IDS[i]);
            int x=iconX(i),y=iconY(i);
            context.getMatrices().push();context.getMatrices().translate(x,y,0);context.getMatrices().scale(2,2,1);
            context.drawItem(new ItemStack(item),0,0);context.getMatrices().pop();
            context.drawTextWithShadow(textRenderer,String.format(Locale.ROOT,"%02d",i+1),x+8,y+34,0xc4d0df);
        }
    }
}
