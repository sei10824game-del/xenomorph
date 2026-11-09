package com.xenomorph.entity;

/** Attack animations. duration/hit are in ticks; min/max are target-distance bounds used by the AI. */
public enum Attack {
    NONE(0, 0, 0, 0),
    CLAW_L(14, 7, 0, 3.4),
    CLAW_R(14, 7, 0, 3.4),
    DOUBLE_CLAW(24, 9, 0, 3.2),     // both claws rake in a cross-slash, 2nd hit at hit+6
    TAIL_STAB(22, 11, 1.5, 5.5),    // coil, thrust over/around body
    TAIL_SWEEP(28, 12, 0, 4.2),     // 360-ish spin sweep, AoE + knockback
    BITE(18, 9, 0, 3.0),            // inner jaw
    GRAB(60, 8, 0, 3.2),            // seize, lift, inner-jaw execution
    POUNCE(34, 14, 4, 11),          // crouch -> leap -> land
    SPIT(26, 12, 5, 16),            // acid spit
    SCREECH(44, 16, 0, 20),         // roar, stun/slow nearby
    CHARGE(50, 10, 7, 22),          // head-down rush (Crusher)
    IMPALE(34, 15, 1.5, 7),         // queen tail impale + lift
    SLAM(30, 14, 0, 4.5),           // overhead double-fist smash, shockwave
    LAY(70, 35, 0, 0);              // queen ovipositor

    public final int duration, hit;
    public final double min, max;

    Attack(int duration, int hit, double min, double max) {
        this.duration = duration; this.hit = hit; this.min = min; this.max = max;
    }
    public float progress(int t) { return duration == 0 ? 0 : Math.min(1f, t / (float) duration); }
}
