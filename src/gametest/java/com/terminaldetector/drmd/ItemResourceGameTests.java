package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import java.nio.file.Files;

/** Enumerate the real registry, including retired saved IDs, rather than guessing IDs from JSON. */
public final class ItemResourceGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyRegisteredDrmdItemHasAnActualModelResource(TestContext context) throws Exception {
        var table=new StringBuilder("item_id\timplementation\tweapon_def\tmodel\trender_check\n");int count=0;
        for(var id:Registries.ITEM.getIds().stream().sorted().toList())if(id.getNamespace().equals("drmd")) {
            var item=Registries.ITEM.get(id);String model="assets/drmd/models/item/"+id.getPath()+".json";
            try(var resource=ItemResourceGameTests.class.getClassLoader().getResourceAsStream(model)) {
                context.assertTrue(resource!=null,"registered item is missing its real model resource: "+id);
                var json=com.google.gson.JsonParser.parseString(new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                context.assertTrue(json.isJsonObject(),"model loader JSON must be an object: "+id);
            }
            table.append(id).append('\t').append(item.getClass().getName()).append('\t')
                .append(item instanceof DescentWeaponItem weapon?weapon.getDef().id:"").append('\t').append(model).append("\tNOT RUN: native model binding only\n");count++;
        }
        context.assertTrue(count>0,"empty runtime registry is not an asset pass");
        Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("item-resource-inventory.tsv"),table);
        context.complete();
    }
}
