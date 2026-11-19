package com.terminaldetector.drmd;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MirrorFallbackAssetsTest {
    private static JsonObject resource(String path)throws IOException{
        try(var stream=MirrorFallbackAssetsTest.class.getResourceAsStream(path)){
            assertNotNull(stream,"actual packaged fallback resource "+path);
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void everyPlainAndChargedFacingHasAnUnconditionalPhysicalPanelWithoutAReflectionTexture()throws IOException{
        for(String block:new String[]{"mirror","charged_mirror"}){
            var states=resource("/assets/drmd/blockstates/"+block+".json");var facings=new HashSet<String>();
            for(var value:states.getAsJsonArray("multipart")){
                var entry=value.getAsJsonObject();String model=entry.getAsJsonObject("apply").get("model").getAsString();
                if(!model.endsWith("_backing"))continue;
                var when=entry.getAsJsonObject("when");facings.add(when.get("facing").getAsString());
                assertFalse(when.has("reflection"),"the physical fallback never depends on a successful offscreen pass");
                var data=resource("/assets/"+model.substring(0,model.indexOf(':'))+"/models/"+model.substring(model.indexOf(':')+1)+".json");boolean panel=false;
                for(var raw:data.getAsJsonArray("elements")){
                    var element=raw.getAsJsonObject();var from=element.getAsJsonArray("from");var to=element.getAsJsonArray("to");
                    boolean volume=true;for(int axis=0;axis<3;axis++)volume&=to.get(axis).getAsDouble()>from.get(axis).getAsDouble();
                    if(volume)for(var face:element.getAsJsonObject("faces").entrySet())panel|=face.getValue().getAsJsonObject().get("texture").getAsString().equals("#panel");
                }
                assertTrue(panel,"real packaged model retains the fallback slab for "+model);
                String texture=data.getAsJsonObject("textures").get("panel").getAsString();
                try(var actual=getClass().getResourceAsStream("/assets/"+texture.substring(0,texture.indexOf(':'))+"/textures/"+texture.substring(texture.indexOf(':')+1)+".png")){
                    assertNotNull(actual,"actual slab texture");var image=javax.imageio.ImageIO.read(actual);assertNotNull(image);int visible=0;
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0)visible++;
                    assertTrue(visible>=16,"fallback texture contains real visible pixels");
                }
            }
            assertEquals(Set.of("north","south","east","west","up","down"),facings);
        }
    }
}
