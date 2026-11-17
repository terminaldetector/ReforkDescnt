package com.terminaldetector.drmd.world.fixture.city;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.zip.CRC32;

/** Worker-only bounded publication. Failed/incomplete files are kept for review. */
final class CityDurableFiles {
    private CityDurableFiles(){}
    static void forceDirectory(Path directory)throws IOException{
        try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
    static Path provisionDirectory(Path parent,String name)throws IOException{
        if(!Files.isDirectory(parent))throw new IOException("city requires an existing save directory");
        Path child=parent.resolve(name);
        try{Files.createDirectory(child);}catch(FileAlreadyExistsException existing){
            if(!Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS))throw new IOException("city path is not an owned directory",existing);
        }
        forceDirectory(parent);forceDirectory(child);return child;
    }
    static byte[] checked(byte[] payload,int maximum)throws IOException{
        if(payload.length>maximum-4)throw new IOException("city metadata byte budget");
        var crc=new CRC32();crc.update(payload);
        return ByteBuffer.allocate(payload.length+4).put(payload).putInt((int)crc.getValue()).array();
    }
    static byte[] readChecked(Path file,int maximum)throws IOException{
        byte[] bytes;
        try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            long size=channel.size();if(size<4||size>maximum)throw new IOException("city metadata byte budget");
            var buffer=ByteBuffer.allocate((int)size);while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new EOFException("city metadata truncated");
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("city metadata grew during read");bytes=buffer.array();
        }
        var crc=new CRC32();crc.update(bytes,0,bytes.length-4);
        if((int)crc.getValue()!=ByteBuffer.wrap(bytes).getInt(bytes.length-4))throw new IOException("city metadata CRC mismatch");
        return java.util.Arrays.copyOf(bytes,bytes.length-4);
    }
    /** The caller holds the save lease; no second legitimate writer can race publication. */
    static void publishNew(Path file,byte[] bytes,int maximum)throws IOException{
        if(bytes.length>maximum)throw new IOException("city metadata byte budget");
        Path parent=file.toAbsolutePath().getParent();
        if(parent==null||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new IOException("city metadata requires provisioned directory");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(file.toString());
        Path pending=file.resolveSibling(file.getFileName()+".pending");
        try(var channel=FileChannel.open(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        forceDirectory(parent);
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(file.toString());
        Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE);forceDirectory(parent);
    }
}
