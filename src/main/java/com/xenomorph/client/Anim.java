package com.xenomorph.client;

import net.minecraft.util.Mth;

/** Tiny keyframe helpers: cosine-eased tracks given as time,value pairs. */
final class Anim {
    private Anim() {}

    /** Eased interpolation through (time,value) keys; clamps outside the range. */
    static float kf(float t, float... k) {
        if (t <= k[0]) return k[1];
        for (int i = 2; i < k.length; i += 2) {
            if (t <= k[i]) {
                float a = k[i - 2], b = k[i];
                float u = (t - a) / (b - a);
                u = 0.5f - 0.5f * Mth.cos(u * Mth.PI);
                return Mth.lerp(u, k[i - 1], k[i + 1]);
            }
        }
        return k[k.length - 1];
    }
    /** Fast-start (ease-out) variant for impact snaps. */
    static float snap(float t, float... k) {
        if (t <= k[0]) return k[1];
        for (int i = 2; i < k.length; i += 2) {
            if (t <= k[i]) {
                float u = (t - k[i - 2]) / (k[i] - k[i - 2]);
                u = 1 - (1 - u) * (1 - u) * (1 - u);
                return Mth.lerp(u, k[i - 1], k[i + 1]);
            }
        }
        return k[k.length - 1];
    }
    static float smooth(float u) { u = Mth.clamp(u, 0, 1); return u * u * (3 - 2 * u); }
    static float[] ik(float tz, float ty, float l1, float l2, boolean kneeFwd) {
        float d = Mth.clamp(Mth.sqrt(tz * tz + ty * ty), 0.5f, l1 + l2 - 0.05f);
        float alpha = (float) Math.atan2(tz, ty);
        float beta = (float) Math.acos(Mth.clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1, 1));
        return kneeFwd ? new float[]{alpha - beta, alpha + beta} : new float[]{alpha + beta, alpha - beta};
    }
}
