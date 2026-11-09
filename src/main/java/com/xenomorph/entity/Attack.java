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
    LAY(70, 35, 0, 0),              // queen ovipositor
    BLADE_FLURRY(34, 6, 0, 3.8),    // whirling blade-arm blender
    HORN_TOSS(30, 13, 0, 3.8),      // head-down gore, launches prey skyward
    STOMP(38, 17, 0, 6),            // leap and crash down, expanding shockwave ring
    GRAB_SLAM(58, 8, 0, 3.4),       // lift overhead, slam into ground twice, hurl
    SHADOW_STRIKE(28, 13, 3, 14),   // vanish, reappear behind, backstab
    IMPREGNATE(64, 8, 0, 3.2),      // pin, ovipositor, embryo
    FRENZY(40, 6, 0, 3.4),          // berserk claw+bite storm with lifesteal
    DEATH_ROLL(56, 8, 0, 3.4),      // clamp and spin like a crocodile
    LEAP_SLASH(30, 12, 3, 10),      // somersault over the target, slashing
    FLAIL(36, 6, 0, 3.6),           // erratic windmill arms
    SPORE(44, 20, 0, 8),            // inflate and burst a poison spore cloud
    LOB(30, 13, 5, 18),             // lobbed acid glob that splashes
    VOLLEY(36, 12, 4, 16),          // three rapid spits
    VOMIT(40, 10, 0, 7),            // acid cone stream
    DETONATE(46, 34, 0, 5),         // swell and burst (kamikaze)
    SUMMON(62, 30, 0, 30),          // call the brood
    FACE_TOSS(32, 14, 3, 14),       // hurl facehuggers
    WEB(26, 11, 4, 14),             // sticky strand, reel prey in
    MULTI_STAB(34, 8, 0, 4.2),      // all legs piston-stab
    CREST_RAM(26, 11, 0, 4),        // crest headbutt, stun
    DROP_STRIKE(30, 10, 0, 0);      // ceiling ambush: drop onto prey (only chosen from the hanging state)

    public final int duration, hit;
    public final double min, max;

    Attack(int duration, int hit, double min, double max) {
        this.duration = duration; this.hit = hit; this.min = min; this.max = max;
    }
    public float progress(int t) { return duration == 0 ? 0 : Math.min(1f, t / (float) duration); }
}
