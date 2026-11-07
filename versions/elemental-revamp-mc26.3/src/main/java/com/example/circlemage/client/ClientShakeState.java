package com.example.circlemage.client;

/**
 * Client-side camera-shake state, driven by the server's SHAKE packet and read by
 * {@link com.example.circlemage.mixin.CameraMixin}. Intensity fades out linearly over the duration.
 */
public final class ClientShakeState {

    private static float intensity = 0.0f;
    private static int ticks = 0;
    private static int maxTicks = 1;

    private ClientShakeState() {
    }

    /** Start (or strengthen) a shake of {@code intensity} degrees for {@code durationTicks}. */
    public static void set(float newIntensity, int durationTicks) {
        if (newIntensity >= intensity || ticks <= 0) {
            intensity = newIntensity;
        }
        if (durationTicks > ticks) {
            ticks = durationTicks;
        }
        maxTicks = Math.max(1, ticks);
    }

    public static void tick() {
        if (ticks > 0) {
            ticks--;
        }
    }

    /** Current shake magnitude in degrees (0 when inactive). */
    public static float current() {
        if (ticks <= 0) {
            return 0.0f;
        }
        return intensity * ((float) ticks / maxTicks);
    }
}
