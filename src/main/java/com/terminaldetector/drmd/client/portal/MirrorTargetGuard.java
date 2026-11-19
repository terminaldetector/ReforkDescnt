package com.terminaldetector.drmd.client.portal;

import java.util.function.Supplier;

/** Bounded allocation failure is a missing reflection, not a missing physical mirror or broken outer frame. */
public final class MirrorTargetGuard {
    public static final long MAX_PIXELS=16L*1024*1024;
    private int failedWidth,failedHeight;
    private String reason="none";
    public String reason(){return reason;}
    public void reset(){failedWidth=failedHeight=0;reason="none";}
    public <T> T acquire(int width,int height,int textureLimit,Supplier<T> allocate,Runnable rollback,Runnable restore){
        if(width<=0||height<=0){reason="outer target has no area";return null;}
        if(width>textureLimit||height>textureLimit||(long)width*height>MAX_PIXELS){reason="reflection target exceeds texture/pixel budget";return null;}
        if(width==failedWidth&&height==failedHeight)return null;
        try{
            T value=allocate.get();if(value==null)throw new IllegalStateException("allocator returned no reflection target");
            reset();return value;
        }catch(RuntimeException failure){
            failedWidth=width;failedHeight=height;reason="reflection target unavailable: "+failure.getClass().getSimpleName()+": "+failure.getMessage();
            try{rollback.run();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);reason+="; cleanup: "+cleanup.getClass().getSimpleName();}
            return null;
        }finally{restore.run();}
    }
}
