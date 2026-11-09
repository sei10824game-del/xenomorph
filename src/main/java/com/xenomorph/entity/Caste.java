package com.xenomorph.entity;

import java.util.EnumSet;
import static com.xenomorph.entity.Attack.*;

/** All adult-form xenomorph castes. biped: 0=quad only, 1=can switch, 2=biped only. */
public enum Caste {
    //          id          hp   dmg spd   scl  stance crest tubes arms dreads color attacks
    DRONE      ("drone",     30, 5, .34f, 1.00f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE, FRENZY, DROP_STRIKE)),
    WARRIOR    ("warrior",   44, 7, .36f, 1.10f, 1, 1, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE, CREST_RAM, DROP_STRIKE)),
    PRAETORIAN ("praetorian",90, 10,.30f, 1.45f, 1, 2, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, CREST_RAM, STOMP)),
    QUEEN      ("queen",    260, 16,.26f, 2.10f, 2, 2, true,  true,  false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SUMMON, CREST_RAM)),
    RUNNER     ("runner",    22, 4, .52f, 0.80f, 0, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_SWEEP, BITE, GRAB, POUNCE, DEATH_ROLL, FRENZY, DROP_STRIKE)),
    CRUSHER    ("crusher",  120, 12,.27f, 1.30f, 0, 3, false, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, TAIL_SWEEP, BITE, HORN_TOSS, STOMP)),
    SPITTER    ("spitter",   34, 5, .33f, 1.05f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, SPIT, VOLLEY, LOB, DROP_STRIKE)),
    RAVAGER    ("ravager",   70, 11,.38f, 1.30f, 1, 1, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE, SCREECH, BLADE_FLURRY, FRENZY, DROP_STRIKE)),
    LURKER     ("lurker",    40, 8, .40f, 1.10f, 1, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE, SHADOW_STRIKE, DROP_STRIKE)),
    SENTINEL   ("sentinel",  28, 5, .38f, 0.95f, 1, 4, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, POUNCE, SPIT, VOLLEY, DROP_STRIKE)),
    CARRIER    ("carrier",   36, 5, .34f, 1.05f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, POUNCE, FACE_TOSS, DROP_STRIKE)),
    PREDALIEN  ("predalien",150, 14,.36f, 1.55f, 1, 5, true,  false, true,  EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, POUNCE, IMPREGNATE, FRENZY, DROP_STRIKE)),
    NEOMORPH   ("neomorph",  36, 7, .42f, 1.00f, 2, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, BITE, POUNCE, FLAIL, SPORE, LEAP_SLASH, DROP_STRIKE)),
    NEWBORN    ("newborn",   80, 9, .34f, 1.50f, 2, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, GRAB, SLAM, BITE, SCREECH, GRAB_SLAM, STOMP, FLAIL)),
    DEACON     ("deacon",    38, 6, .40f, 1.00f, 1, 7, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE, LEAP_SLASH, DROP_STRIKE)),
    BOILER     ("boiler",    50, 6, .28f, 1.25f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_SWEEP, BITE, SPIT, VOMIT, LOB, DROP_STRIKE)),
    PROTOMORPH ("protomorph",55, 9, .38f, 1.15f, 1, 1, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE, DEATH_ROLL, DROP_STRIKE)),
    STALKER    ("stalker",   45, 8, .46f, 1.05f, 1, 4, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE, SHADOW_STRIKE, BLADE_FLURRY, DROP_STRIKE)),
    MATRIARCH  ("matriarch", 320, 18, 0.26f, 2.3f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SUMMON, VOLLEY)),
    EMPRESS    ("empress", 400, 20, 0.25f, 2.5f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT, SUMMON, STOMP)),
    QUEEN_MOTHER ("queen_mother", 600, 24, 0.24f, 3.0f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT, SUMMON, STOMP, GRAB_SLAM)),
    KING       ("king", 300, 22, 0.28f, 2.2f, 2, 3, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, CHARGE, GRAB_SLAM, STOMP, HORN_TOSS)),
    CHARGER    ("charger", 90, 11, 0.45f, 1.3f, 0, 3, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, CHARGE, POUNCE, HORN_TOSS)),
    BRUTE      ("brute", 110, 13, 0.3f, 1.5f, 1, 5, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, SLAM, CHARGE, TAIL_SWEEP, BITE, GRAB, STOMP, CREST_RAM)),
    PATHOGEN_RUNNER ("pathogen_runner", 24, 4, 0.52f, 0.85f, 0, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, POUNCE, DEATH_ROLL, FRENZY, DROP_STRIKE)),
    PATHOGEN_BLIGHT ("pathogen_blight", 40, 5, 0.34f, 1.05f, 1, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, SPIT, LOB, SPORE, DROP_STRIKE)),
    PATHOGEN_BRUTE ("pathogen_brute", 140, 15, 0.27f, 1.6f, 1, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, GRAB, BITE, STOMP, GRAB_SLAM)),
    PATHOGEN_QUEEN ("pathogen_queen", 280, 17, 0.26f, 2.1f, 2, 6, false, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT, SUMMON, SPORE)),
    BURSTER    ("burster", 30, 6, 0.4f, 0.9f, 0, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, POUNCE, DETONATE, DROP_STRIKE)),
    PROWLER    ("prowler", 42, 8, 0.44f, 1.0f, 1, 4, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, POUNCE, SHADOW_STRIKE, BLADE_FLURRY, DROP_STRIKE)),
    BULL_ALIEN ("bull_alien", 130, 13, 0.3f, 1.4f, 1, 3, true, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, BITE, TAIL_SWEEP, HORN_TOSS)),
    ARACHNID   ("arachnid", 60, 8, 0.4f, 1.2f, 0, 0, false, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE, WEB, MULTI_STAB, DROP_STRIKE)),
    // super-predalien: scarred, towering, on the Predalien frame
    BERSERKER  ("berserker", 320, 20, .40f, 1.9f, 1, 5, true, false, true, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, POUNCE, IMPREGNATE, FRENZY, STOMP, GRAB_SLAM, BLADE_FLURRY, CHARGE)),
    // Aliens: Genocide - red strain that wages war on the black hive: kill first, ask never
    RED_DRONE  ("red_drone",  38, 8, .40f, 1.00f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, POUNCE, FRENZY, BLADE_FLURRY, DROP_STRIKE)),
    RED_WARRIOR("red_warrior",58, 10,.41f, 1.10f, 1, 1, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, POUNCE, FRENZY, CREST_RAM, STOMP, DROP_STRIKE)),
    RED_QUEEN_MOTHER("red_queen_mother", 380, 22, .27f, 2.4f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SUMMON, STOMP, GRAB_SLAM, CREST_RAM, FRENZY));

    public final String id;
    public final float hp, dmg, speed, scale;
    public final int stance, crest;
    public final boolean tubes, extraArms, dreads;
    public final EnumSet<Attack> attacks;

    Caste(String id, float hp, float dmg, float speed, float scale, int stance, int crest, boolean tubes,
          boolean extraArms, boolean dreads, EnumSet<Attack> attacks) {
        this.id = id; this.hp = hp; this.dmg = dmg; this.speed = speed; this.scale = scale; this.stance = stance;
        this.crest = crest; this.tubes = tubes; this.extraArms = extraArms; this.dreads = dreads; this.attacks = attacks;
    }
    public boolean canBiped() { return stance >= 1; }
    public boolean canQuad() { return stance <= 1; }

    /** Body mass feel: >1 = heavy (bigger knockback, slower gait, impact shake); <1 = light. */
    public float weight() {
        return switch (this) {
            case QUEEN_MOTHER -> 2f; case RED_QUEEN_MOTHER -> 1.9f; case RED_WARRIOR, RED_DRONE -> 1.05f; case BERSERKER -> 1.8f; case PATHOGEN_BRUTE -> 1.8f; case CRUSHER, BRUTE -> 1.7f; case KING, EMPRESS -> 1.6f; case BULL_ALIEN, NEWBORN -> 1.5f;
            case QUEEN, CHARGER -> 1.4f; case PRAETORIAN, MATRIARCH, PATHOGEN_QUEEN -> 1.3f; case BOILER -> 1.2f;
            case RUNNER, PATHOGEN_RUNNER -> 0.7f; case DEACON -> 0.8f; case LURKER, PROWLER, STALKER -> 0.85f; case NEOMORPH, BURSTER -> 0.9f; default -> 1f; };
    }
    /** Temper 0..1: fast recovery, combo chaining, berserk below 40% hp (>=0.5), twitchy idle. */
    public float ferocity() {
        return switch (this) {
            case PREDALIEN, BERSERKER, RED_QUEEN_MOTHER -> 1f; case RED_WARRIOR, RED_DRONE -> 0.85f; case RAVAGER, NEOMORPH -> 0.9f; case RUNNER, PATHOGEN_RUNNER, NEWBORN -> 0.8f; case BULL_ALIEN, CHARGER -> 0.7f;
            case STALKER, PROWLER, DEACON, KING -> 0.6f; case BRUTE, QUEEN, PATHOGEN_BRUTE -> 0.5f; case QUEEN_MOTHER -> 0.45f; default -> 0.25f; };
    }
    public boolean red() { return id.startsWith("red_"); }
}
