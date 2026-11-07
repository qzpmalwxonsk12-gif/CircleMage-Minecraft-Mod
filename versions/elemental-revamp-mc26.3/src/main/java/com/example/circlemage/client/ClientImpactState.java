package com.example.circlemage.client;
/** White impact flash and additive desaturation request, without replacing other post effects. */
public final class ClientImpactState {
    private static int ticks, maxTicks=1;
    private ClientImpactState() {}
    public static void trigger(int durationTicks) { ticks=Math.max(ticks,durationTicks); maxTicks=Math.max(1,ticks); }
    public static void tick() { if(ticks>0) ticks--; }
    public static boolean active() { return ticks>0; }
    public static float flashAlpha() { return ticks<=0 ? 0 : (float)ticks/maxTicks; }
}
