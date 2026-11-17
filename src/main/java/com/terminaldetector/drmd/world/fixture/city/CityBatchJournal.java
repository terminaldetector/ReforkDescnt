package com.terminaldetector.drmd.world.fixture.city;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.CRC32;

/** Durable byte acknowledgments only. Runtime calls belong on its bounded I/O worker,
 * never inside a world tick; no Minecraft references or automatic replay exist here. */
public final class CityBatchJournal implements AutoCloseable {
    public static final int MAX_CELLS=64,MAX_FILE_BYTES=8*1024*1024,MAX_FRAME_BYTES=64*1024;
    private static final int MAGIC=0x43495459,VERSION=1;
    public record Identity(UUID owner,String world,String planHash){
        public Identity{Objects.requireNonNull(owner);if(world==null||!world.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||world.length()>128||planHash==null||!planHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid city journal identity");}
    }
    public record Change(CityBlueprint.Point position,String before,String after){
        public Change{Objects.requireNonNull(position);state(before);state(after);}
    }
    public record Batch(long number,int startCursor,int advance,List<Change> changes){
        public Batch{
            changes=List.copyOf(changes);if(number<0||number>131_072||startCursor<0||advance<1||advance>MAX_CELLS||changes.size()!=advance||(long)startCursor+advance>131_072)throw new IllegalArgumentException("city batch bounds");
            var unique=new HashSet<CityBlueprint.Point>();for(var change:changes)if(!unique.add(change.position()))throw new IllegalArgumentException("duplicate city cell");
        }
    }
    public record Snapshot(Identity identity,int cursor,long nextBatch,Optional<Batch> pending,boolean incompleteTail){}
    public enum Recovery { RETRY,ALREADY_COMMITTED,CONFLICT,UNAVAILABLE }
    public static Recovery reconcile(Batch batch,Function<CityBlueprint.Point,Optional<String>> read){
        boolean before=true,after=true;
        for(var change:batch.changes()){
            var actual=Objects.requireNonNull(read.apply(change.position()));if(actual.isEmpty())return Recovery.UNAVAILABLE;
            boolean old=actual.get().equals(change.before()),next=actual.get().equals(change.after());if(!old&&!next)return Recovery.CONFLICT;before&=old;after&=next;
        }
        return after?Recovery.ALREADY_COMMITTED:before?Recovery.RETRY:Recovery.CONFLICT;
    }
    private final FileChannel channel;private final FileLock lock;private final Identity identity;
    private int cursor;private long nextBatch;private Batch pending;private boolean incomplete,closed;
    private CityBatchJournal(FileChannel channel,FileLock lock,Identity identity){this.channel=channel;this.lock=lock;this.identity=identity;}
    /** Open is explicit operator/test I/O, not a registration/startup hook. */
    public static CityBatchJournal open(Path path,Identity expected) throws IOException {
        Objects.requireNonNull(path);Objects.requireNonNull(expected);Path parent=path.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);FileLock lock=null;
        try{
            try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("city journal already owned",busy);}
            if(lock==null)throw new IOException("city journal already owned");var journal=new CityBatchJournal(channel,lock,expected);
            if(channel.size()==0){journal.append(0,out->{out.writeInt(MAGIC);out.writeInt(VERSION);writeIdentity(out,expected);});}
            else journal.load();return journal;
        }catch(IOException|RuntimeException failure){if(lock!=null)lock.release();channel.close();throw failure;}
    }
    public Snapshot snapshot(){return new Snapshot(identity,cursor,nextBatch,Optional.ofNullable(pending),incomplete);}
    public void prepare(Batch batch) throws IOException {
        writable();Objects.requireNonNull(batch);
        if(batch.equals(pending))return;
        if(pending!=null||batch.number()!=nextBatch||batch.startCursor()!=cursor)throw new IOException("city batch does not match durable prefix");
        append(1,out->writeBatch(out,batch));pending=batch;
    }
    public void commit(long number) throws IOException {
        writable();if(number>=0&&number<nextBatch)return;
        if(pending==null||pending.number()!=number)throw new IOException("city commit without matching prepared batch");
        append(2,out->out.writeLong(number));cursor=pending.startCursor()+pending.advance();pending=null;nextBatch++;
    }
    private void writable() throws IOException {if(closed||incomplete)throw new IOException("closed or quarantined city journal");}
    @FunctionalInterface private interface Encoder{void write(DataOutputStream out)throws IOException;}
    private void append(int type,Encoder encoder) throws IOException {
        writable();var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeByte(type);encoder.write(out);}byte[] payload=bytes.toByteArray();
        if(payload.length>MAX_FRAME_BYTES||channel.size()+payload.length+8>MAX_FILE_BYTES)throw new IOException("city journal byte budget");
        var crc=new CRC32();crc.update(payload);var frame=ByteBuffer.allocate(payload.length+8);frame.putInt(payload.length).put(payload).putInt((int)crc.getValue()).flip();
        try{channel.position(channel.size());while(frame.hasRemaining())channel.write(frame);channel.force(true);}
        catch(IOException failure){incomplete=true;throw failure;}
    }
    private void load() throws IOException {
        long size=channel.size();if(size>MAX_FILE_BYTES)throw new IOException("city journal byte budget");var bytes=ByteBuffer.allocate((int)size);channel.position(0);
        while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new EOFException("city journal changed during read");bytes.flip();boolean header=false;
        try{
            while(bytes.hasRemaining()){
                if(bytes.remaining()<4){incomplete=true;break;}int length=bytes.getInt();if(length<1||length>MAX_FRAME_BYTES)throw new IOException("invalid city frame size");
                if(bytes.remaining()<length+4){incomplete=true;break;}byte[] payload=new byte[length];bytes.get(payload);int checksum=bytes.getInt();var crc=new CRC32();crc.update(payload);
                if((int)crc.getValue()!=checksum)throw new IOException("city frame CRC mismatch");
                try(var in=new DataInputStream(new ByteArrayInputStream(payload))){
                    int type=in.readUnsignedByte();
                    if(type==0){if(header||in.readInt()!=MAGIC||in.readInt()!=VERSION||!readIdentity(in).equals(identity))throw new IOException("foreign or unsupported city journal");header=true;}
                    else if(type==1){var batch=readBatch(in);if(!header||pending!=null||batch.number()!=nextBatch||batch.startCursor()!=cursor)throw new IOException("noncontiguous city prepare");pending=batch;}
                    else if(type==2){long number=in.readLong();if(!header||pending==null||pending.number()!=number)throw new IOException("noncontiguous city commit");cursor=pending.startCursor()+pending.advance();pending=null;nextBatch++;}
                    else throw new IOException("unknown city journal frame");
                    if(in.available()!=0)throw new IOException("trailing city frame data");
                }
            }
        }catch(IllegalArgumentException invalid){throw new IOException("invalid city journal content",invalid);}
        if(!header)throw new IOException("missing city journal identity");
    }
    private static void state(String key){if(key==null||key.length()>256||!key.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+(\\[[a-z0-9_=,.-]+\\])?"))throw new IllegalArgumentException("invalid block state key");}
    private static void writeIdentity(DataOutputStream out,Identity id)throws IOException{out.writeLong(id.owner().getMostSignificantBits());out.writeLong(id.owner().getLeastSignificantBits());out.writeUTF(id.world());out.writeUTF(id.planHash());}
    private static Identity readIdentity(DataInputStream in)throws IOException{return new Identity(new UUID(in.readLong(),in.readLong()),in.readUTF(),in.readUTF());}
    private static void writeBatch(DataOutputStream out,Batch b)throws IOException{
        out.writeLong(b.number());out.writeInt(b.startCursor());out.writeInt(b.advance());var palette=new LinkedHashMap<String,Integer>();
        for(var c:b.changes()){palette.computeIfAbsent(c.before(),s->palette.size());palette.computeIfAbsent(c.after(),s->palette.size());}
        out.writeShort(palette.size());for(var key:palette.keySet())out.writeUTF(key);
        for(var c:b.changes()){out.writeInt(c.position().x());out.writeInt(c.position().y());out.writeInt(c.position().z());out.writeShort(palette.get(c.before()));out.writeShort(palette.get(c.after()));}
    }
    private static Batch readBatch(DataInputStream in)throws IOException{
        long number=in.readLong();int cursor=in.readInt(),advance=in.readInt(),count=in.readUnsignedShort();if(advance<1||advance>MAX_CELLS||count<1||count>2*MAX_CELLS)throw new IOException("city frame cell/palette bounds");
        var palette=new ArrayList<String>();for(int i=0;i<count;i++){var key=in.readUTF();state(key);palette.add(key);}var changes=new ArrayList<Change>();
        for(int i=0;i<advance;i++){var p=new CityBlueprint.Point(in.readInt(),in.readInt(),in.readInt());int old=in.readUnsignedShort(),next=in.readUnsignedShort();if(old>=count||next>=count)throw new IOException("city palette index");changes.add(new Change(p,palette.get(old),palette.get(next)));}
        return new Batch(number,cursor,advance,changes);
    }
    @Override public void close() throws IOException {if(closed)return;closed=true;try{lock.release();}finally{channel.close();}}
}
