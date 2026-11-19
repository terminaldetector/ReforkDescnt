package com.terminaldetector.drmd.aerisfixture;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.terminaldetector.drmd.aeris.AerisDensityChunkGenerator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.*;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import java.nio.charset.StandardCharsets;

/** Dedicated real profile controls, not part of ordinary auto-all-packs aggregate. */
public final class AerisPackAdmissionGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aeris-real-profile")
    public void actualUnoptedAndExplicitRegistriesRetainOldCodecWithoutAddingAnOrdinaryWorld(TestContext c){
        c.assertTrue(AerisProfileState.ENABLED&&AerisProfileState.selected,"actual profile must be captured before registry load");
        var id=Identifier.of("drmd","aeris_test");var key=RegistryKey.of(RegistryKeys.WORLD,id);var server=c.getWorld().getServer();var demo=server.getWorld(key);
        boolean explicit=AerisProfileState.MODE.equals("explicit");
        c.assertTrue(AerisProfileState.after.contains(AerisProfileState.DEMO)==explicit,"real pack selection matches explicit intent");
        c.assertTrue(!AerisProfileState.after.contains(AerisProfileState.ADVANCED),"demo does not reenable retired Advanced profile");
        c.assertTrue((demo!=null)==explicit,"real server world existence follows actual selected pack, not synthetic dimension values");
        c.assertTrue(server.getOverworld().getBottomY()==-64&&server.getOverworld().getTopY()==320,"ordinary Overworld bounds survive either profile");
        if(explicit)c.assertTrue(demo.getBottomY()==-128&&demo.getHeight()==384&&demo.getChunkManager().getChunkGenerator() instanceof AerisDensityChunkGenerator,"explicit demo restores original generator/type/bounds");
        c.assertTrue(Registries.CHUNK_GENERATOR.get(Identifier.of("drmd","aeris_density_generator"))==AerisDensityChunkGenerator.CODEC,"original saved generator codec ID remains registered");
        try(var in=AerisPackAdmissionGameTests.class.getResourceAsStream("/resourcepacks/aeris_demo/data/drmd/dimension/aeris_test.json")){
            c.assertTrue(in!=null,"exact original saved dimension option remains packaged in the opt-in demo");
            var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            var decoded=DimensionOptions.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE,c.getWorld().getRegistryManager()),json).getOrThrow();
            c.assertTrue(decoded.chunkGenerator() instanceof AerisDensityChunkGenerator,"original serialized dimension option decodes even when demo world is unopted");
            c.assertTrue(decoded.dimensionTypeEntry().getKey().orElseThrow().getValue().equals(id),"base old dimension type is retained for intended saves");
        }catch(Exception e){throw new IllegalStateException(e);}
        c.complete();
    }
}
