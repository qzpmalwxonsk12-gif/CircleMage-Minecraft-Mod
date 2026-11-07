package com.example.circlemage.client;

import com.example.circlemage.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Client-side impact-frame state for the Hollow Purple detonation: for a few ticks it desaturates the
 * screen (a post shader) and paints a white blowout flash (HUD overlay). Both fade out together.
 * The post shader is best-effort — if it fails to load, the white flash still plays.
 */
public final class ClientImpactState {

    private static final Identifier DESATURATE = new Identifier("circlemage", "shaders/post/desaturate.json");

    private static int ticks = 0;
    private static int maxTicks = 1;
    private static boolean shaderActive = false;

    private ClientImpactState() {
    }

    public static void trigger(int durationTicks) {
        ticks = Math.max(ticks, durationTicks);
        maxTicks = Math.max(1, ticks);
        if (!shaderActive) {
            try {
                ((GameRendererAccessor) MinecraftClient.getInstance().gameRenderer)
                        .circlemage$loadPostProcessor(DESATURATE);
                shaderActive = true;
            } catch (Throwable t) {
                shaderActive = false; // graceful: the white flash still conveys the impact frame
            }
        }
    }

    public static void tick() {
        if (ticks > 0) {
            ticks--;
            if (ticks <= 0 && shaderActive) {
                try {
                    ((GameRendererAccessor) MinecraftClient.getInstance().gameRenderer)
                            .circlemage$disablePostProcessor();
                } catch (Throwable ignored) {
                    // ignore
                }
                shaderActive = false;
            }
        }
    }

    /** White flash opacity (0..1), strongest at the start of the impact frame. */
    public static float flashAlpha() {
        if (ticks <= 0) {
            return 0.0f;
        }
        return (float) ticks / maxTicks;
    }
}
