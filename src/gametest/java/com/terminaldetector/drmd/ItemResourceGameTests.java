package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import java.nio.file.Files;
import java.util.List;

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

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void markedEquipmentAndRetiredWeaponIdsRoundTripThroughTheSavedStackCodec(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        // Audit.4 inventory entries and the separately documented legacy weapon identities.
        for (String path : List.of("plasma_granite", "enclave_herald", "drill_rig", "tunnel_drill_rig", "gravity_scanner", "cyclic_laser_kit",
                "weapon_d6_heavy", "weapon_d6_railmk2", "weapon_d6_reactor", "weapon_d6_telefrag", "weapon_d6_whiplash")) {
            var id = Identifier.of("drmd", path);
            var saved = new NbtCompound();
            saved.putString("id", id.toString());
            saved.putInt("count", 1);
            var stack = ItemStack.fromNbt(registries, saved).orElse(ItemStack.EMPTY);
            context.assertTrue(!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(id), "saved identity resolves to the exact registered item " + id);
            var encoded = stack.encode(registries);
            context.assertTrue(encoded instanceof NbtCompound tag && tag.getString("id").equals(id.toString()), "ordinary save preserves the same registry ID " + id);
        }
        context.complete();
    }
}
