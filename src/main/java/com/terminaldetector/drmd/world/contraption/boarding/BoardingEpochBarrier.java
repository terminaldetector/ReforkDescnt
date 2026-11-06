package com.terminaldetector.drmd.world.contraption.boarding;

/** A closed movement authority cannot be reopened by delayed network state.
 * Geometry may still be loading when an external vanilla position transfer arrives. */
public final class BoardingEpochBarrier {
    private int newest=-1,retired=-1;
    public boolean accepts(int epoch){return epoch>=0&&epoch>=newest&&epoch>retired;}
    public void observe(int epoch){if(accepts(epoch))newest=epoch;}
    public void retireThrough(int epoch){retired=Math.max(retired,epoch);}
    public void retireSeen(){retireThrough(newest);}
    public void reset(){newest=retired=-1;}
}
