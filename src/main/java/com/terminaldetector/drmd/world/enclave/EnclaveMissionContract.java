package com.terminaldetector.drmd.world.enclave;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Immutable bounded mission data; no world or entity execution. */
public record EnclaveMissionContract(String id,int revision,String offeredKind,String titleKey,int difficulty,List<Step> objectives) {
    public static final int SCHEMA=1,MAX_OBJECTIVES=8,MAX_BYTES=65_536;
    private static final String ID="[a-z0-9_.-]+:[a-z0-9_./-]+";
    public enum Type { ENTER_VOLUME,BREAK_BLOCK }
    public record Point(int x,int y,int z){
        public Point add(Point other){return new Point(Math.addExact(x,other.x),Math.addExact(y,other.y),Math.addExact(z,other.z));}
    }
    /** Signed integer half-open box, never packed into BlockPos.asLong. */
    public record Box(Point min,Point max){
        public Box{
            Objects.requireNonNull(min);Objects.requireNonNull(max);
            if((long)max.x-min.x<1||(long)max.y-min.y<1||(long)max.z-min.z<1
                ||(long)max.x-min.x>32||(long)max.y-min.y>32||(long)max.z-min.z>32)throw new IllegalArgumentException("objective box must span1..32 per axis");
        }
        public Box add(Point offset){return new Box(min.add(offset),max.add(offset));}
        public boolean contains(Point point){return point.x>=min.x&&point.x<max.x&&point.y>=min.y&&point.y<max.y&&point.z>=min.z&&point.z<max.z;}
    }
    public record Step(Type type,Box volume,Point block,String expectedBlock){
        public Step{
            Objects.requireNonNull(type);
            if(type==Type.ENTER_VOLUME){if(volume==null||block!=null||expectedBlock!=null)throw new IllegalArgumentException("entry requires only a volume");}
            else if(volume!=null||block==null||expectedBlock==null||expectedBlock.length()>160||!expectedBlock.matches(ID)||expectedBlock.equals("minecraft:air"))throw new IllegalArgumentException("break requires an actual non-air block");
        }
        public Step add(Point anchor){return type==Type.ENTER_VOLUME?new Step(type,volume.add(anchor),null,null):new Step(type,null,block.add(anchor),expectedBlock);}
    }
    public EnclaveMissionContract{
        if(id==null||!id.matches(ID)||id.length()>160||revision<1||revision>1_000_000)throw new IllegalArgumentException("invalid mission ID/revision");
        if(!"EXPLORE_SHAFT".equals(offeredKind))throw new IllegalArgumentException("this slice supports only the existing EXPLORE_SHAFT adapter");
        if(titleKey==null||titleKey.isBlank()||titleKey.length()>160||difficulty<1||difficulty>3)throw new IllegalArgumentException("invalid title/difficulty");
        objectives=List.copyOf(objectives);
        if(objectives.isEmpty()||objectives.size()>MAX_OBJECTIVES||objectives.getFirst().type()!=Type.ENTER_VOLUME)throw new IllegalArgumentException("ordered1..8objectives must start with entry");
        if(objectives.stream().noneMatch(s->s.type()==Type.BREAK_BLOCK))throw new IllegalArgumentException("infiltration needs an actual block objective");
        var breakTargets=new HashSet<Point>();
        for(var step:objectives)if(step.type()==Type.BREAK_BLOCK&&!breakTargets.add(step.block()))
            throw new IllegalArgumentException("repeated break targets need a separate interaction identity");
    }
    public String hash(){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(toJson().toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public List<Step> at(Point anchor){return objectives.stream().map(step->step.add(anchor)).toList();}
    public JsonObject toJson(){
        var json=new JsonObject();json.addProperty("schema",SCHEMA);json.addProperty("id",id);json.addProperty("revision",revision);
        json.addProperty("offered_kind",offeredKind);json.addProperty("title",titleKey);json.addProperty("difficulty",difficulty);json.addProperty("reputation_reward",3);
        var list=new JsonArray();for(var step:objectives)list.add(stepJson(step));json.add("objectives",list);return json;
    }
    public static EnclaveMissionContract parse(byte[] bytes){
        var json=boundedJson(bytes,MAX_BYTES);keys(json,"schema","id","revision","offered_kind","title","difficulty","reputation_reward","objectives");
        if(integer(json,"schema")!=SCHEMA||integer(json,"reputation_reward")!=3)throw new IllegalArgumentException("unsupported schema/reward policy");
        var array=json.getAsJsonArray("objectives");if(array==null||array.size()>MAX_OBJECTIVES)throw new IllegalArgumentException("objective cap8");
        var steps=new ArrayList<Step>();for(var raw:array)steps.add(step(raw.getAsJsonObject()));
        return new EnclaveMissionContract(text(json,"id"),integer(json,"revision"),text(json,"offered_kind"),text(json,"title"),integer(json,"difficulty"),steps);
    }
    static JsonObject boundedJson(byte[] bytes,int cap){
        if(bytes.length>cap)throw new IllegalArgumentException("mission payload cap exceeded");
        final String value;
        try{value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
        catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException("invalid UTF-8 mission payload",invalid);}
        int depth=0;boolean quoted=false,escape=false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{'||c=='['){if(++depth>16)throw new IllegalArgumentException("mission nesting cap16");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("invalid mission nesting");}
        }
        if(quoted||depth!=0)throw new IllegalArgumentException("incomplete mission JSON");
        try(var reader=new JsonReader(new StringReader(value))){
            reader.setLenient(false);verifyJson(reader,0,new int[]{0});
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("trailing mission JSON");
        }catch(IOException invalid){throw new IllegalArgumentException("invalid mission JSON",invalid);}
        return JsonParser.parseString(value).getAsJsonObject();
    }
    private static void verifyJson(JsonReader reader,int depth,int[] tokens)throws IOException{
        if(depth>16||++tokens[0]>65_536)throw new IllegalArgumentException("mission JSON work cap");
        switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();var names=new HashSet<String>();while(reader.hasNext()){if(!names.add(reader.nextName()))throw new IllegalArgumentException("duplicate mission field");verifyJson(reader,depth+1,tokens);}reader.endObject();}
            case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())verifyJson(reader,depth+1,tokens);reader.endArray();}
            case STRING,NUMBER->reader.nextString();
            case BOOLEAN->reader.nextBoolean();
            case NULL->reader.nextNull();
            default->throw new IllegalArgumentException("invalid mission JSON value");
        }
    }
    static void keys(JsonObject json,String... expected){if(!json.keySet().equals(Set.of(expected)))throw new IllegalArgumentException("unknown/missing mission fields");}
    static String text(JsonObject json,String key){var v=json.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("string required: "+key);return v.getAsString();}
    static java.math.BigDecimal number(JsonObject json,String key){var v=json.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("number required: "+key);return v.getAsBigDecimal();}
    static int integer(JsonObject json,String key){return number(json,key).intValueExact();}
    static long longValue(JsonObject json,String key){return number(json,key).longValueExact();}
    static boolean bool(JsonObject json,String key){var v=json.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("boolean required: "+key);return v.getAsBoolean();}
    static JsonObject pointJson(Point point){var j=new JsonObject();j.addProperty("x",point.x);j.addProperty("y",point.y);j.addProperty("z",point.z);return j;}
    static Point point(JsonObject json){keys(json,"x","y","z");return new Point(integer(json,"x"),integer(json,"y"),integer(json,"z"));}
    static JsonObject stepJson(Step step){
        var j=new JsonObject();j.addProperty("type",step.type.name());
        if(step.type==Type.ENTER_VOLUME){j.add("min",pointJson(step.volume.min));j.add("max",pointJson(step.volume.max));}
        else{j.add("position",pointJson(step.block));j.addProperty("block",step.expectedBlock);}
        return j;
    }
    static Step step(JsonObject json){
        var type=Type.valueOf(text(json,"type"));
        if(type==Type.ENTER_VOLUME){keys(json,"type","min","max");return new Step(type,new Box(point(json.getAsJsonObject("min")),point(json.getAsJsonObject("max"))),null,null);}
        keys(json,"type","position","block");return new Step(type,null,point(json.getAsJsonObject("position")),text(json,"block"));
    }
}
