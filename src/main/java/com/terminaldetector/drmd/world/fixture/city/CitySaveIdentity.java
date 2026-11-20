package com.terminaldetector.drmd.world.fixture.city;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Explicit worker-owned save identity/lease. No Minecraft references or startup work. */
public final class CitySaveIdentity {
    private static final int MAGIC=0x43534944,VERSION=1,MAX_BYTES=128;
    private CitySaveIdentity(){}
    public static Lease create(Path existingSaveRoot)throws IOException{return acquire(existingSaveRoot,true);}
    public static Lease openExisting(Path existingSaveRoot)throws IOException{return acquire(existingSaveRoot,false);}
    private static Lease acquire(Path saveRoot,boolean provision)throws IOException{
        Objects.requireNonNull(saveRoot);if(!Files.isDirectory(saveRoot))throw new IOException("city requires existing save root");
        Path directory;
        if(provision)directory=CityDurableFiles.provisionDirectory(CityDurableFiles.provisionDirectory(saveRoot,"drmd"),"city");
        else{
            directory=saveRoot.resolve("drmd/city");
            if(!Files.isDirectory(saveRoot.resolve("drmd"),LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("no existing owned city save identity directory");
        }
        Path lockPath=directory.resolve("ownership.lock");
        FileChannel channel=provision?FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
            :FileChannel.open(lockPath,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        FileLock lock=null;
        try{
            try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("city save is already owned",busy);}
            if(lock==null)throw new IOException("city save is already owned");
            channel.force(true);CityDurableFiles.forceDirectory(directory);
            Path idPath=directory.resolve("save-id.bin");
            if(Files.exists(directory.resolve("save-id.bin.pending"),LinkOption.NOFOLLOW_LINKS))throw new IOException("partial city save identity requires review");
            UUID id;
            if(Files.exists(idPath,LinkOption.NOFOLLOW_LINKS))id=read(idPath);
            else{
                if(!provision)throw new IOException("missing existing city save identity");
                for(String file:List.of("current.manifest","current.journal","current.manifest.pending","current.journal.pending"))
                    if(Files.exists(directory.resolve(file),LinkOption.NOFOLLOW_LINKS))throw new IOException("city data exists without its save identity");
                id=UUID.randomUUID();var bytes=new ByteArrayOutputStream();
                try(var out=new DataOutputStream(bytes)){out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
                CityDurableFiles.publishNew(idPath,CityDurableFiles.checked(bytes.toByteArray(),MAX_BYTES),MAX_BYTES);
            }
            return new Lease(directory,id,channel,lock);
        }catch(IOException|RuntimeException failure){
            try{if(lock!=null)lock.release();}catch(IOException close){failure.addSuppressed(close);}
            try{channel.close();}catch(IOException close){failure.addSuppressed(close);}throw failure;
        }
    }
    static UUID read(Path file)throws IOException{
        try(var in=new DataInputStream(new ByteArrayInputStream(CityDurableFiles.readChecked(file,MAX_BYTES)))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("unsupported city save identity");
            UUID id=new UUID(in.readLong(),in.readLong());if(in.available()!=0)throw new IOException("trailing city identity data");return id;
        }
    }
    public static final class Lease implements AutoCloseable{
        private final Path directory;private final UUID saveId;private final FileChannel channel;private final FileLock lock;private boolean closed;
        private Lease(Path directory,UUID saveId,FileChannel channel,FileLock lock){this.directory=directory;this.saveId=saveId;this.channel=channel;this.lock=lock;}
        public Path directory(){return directory;}
        public UUID saveId(){return saveId;}
        @Override public void close()throws IOException{if(closed)return;closed=true;try{lock.release();}finally{channel.close();}}
    }
}
