package com.terminaldetector.drmd.client.cloud;

/** Exact observed cloud layouts and legal sampler API ownership; no geometry or world state. */
public final class CloudRenderCompatibility {
    public enum Layout { SODIUM_POSITION_COLOR, VANILLA_TEXTURED }
    public enum SamplerApi { NONE, CORE_33, ARB }
    private CloudRenderCompatibility(){}
    public static Layout layout(int stride,int position,int uv,int colour,int normal,int elements){
        if(stride==16&&position==0&&uv==-1&&colour==12&&normal==-1&&elements==2)return Layout.SODIUM_POSITION_COLOR;
        if(stride==28&&position==0&&uv==12&&colour==20&&normal==24&&elements==4)return Layout.VANILLA_TEXTURED;
        return null;
    }
    public static SamplerApi samplerApi(boolean core33,boolean arb){return core33?SamplerApi.CORE_33:arb?SamplerApi.ARB:SamplerApi.NONE;}
    public static int farSurfaceUnit(Layout layout){if(layout==null)throw new IllegalArgumentException("Unsupported cloud layout");return layout==Layout.VANILLA_TEXTURED?1:0;}
}
