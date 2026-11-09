package com.xenomorph.entity;

import java.util.EnumSet;
import static com.xenomorph.entity.Attack.*;

/** All adult-form xenomorph castes. biped: 0=quad only, 1=can switch, 2=biped only. */
public enum Caste {
    //          id          hp   dmg spd   scl  stance crest tubes arms dreads color attacks
    DRONE      ("drone",     30, 5, .34f, 1.00f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE)),
    WARRIOR    ("warrior",   44, 7, .36f, 1.10f, 1, 1, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE)),
    PRAETORIAN ("praetorian",90, 10,.30f, 1.45f, 1, 2, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH)),
    QUEEN      ("queen",    260, 16,.26f, 2.10f, 2, 2, true,  true,  false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY)),
    RUNNER     ("runner",    22, 4, .52f, 0.80f, 0, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_SWEEP, BITE, GRAB, POUNCE)),
    CRUSHER    ("crusher",  120, 12,.27f, 1.30f, 0, 3, false, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, TAIL_SWEEP, BITE)),
    SPITTER    ("spitter",   34, 5, .33f, 1.05f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, SPIT)),
    RAVAGER    ("ravager",   70, 11,.38f, 1.30f, 1, 1, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE, SCREECH)),
    LURKER     ("lurker",    40, 8, .40f, 1.10f, 1, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE)),
    SENTINEL   ("sentinel",  28, 5, .38f, 0.95f, 1, 4, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, POUNCE, SPIT)),
    CARRIER    ("carrier",   36, 5, .34f, 1.05f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_STAB, BITE, POUNCE)),
    PREDALIEN  ("predalien",150, 14,.36f, 1.55f, 1, 5, true,  false, true,  EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, POUNCE)),
    NEOMORPH   ("neomorph",  36, 7, .42f, 1.00f, 2, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, BITE, POUNCE)),
    NEWBORN    ("newborn",   80, 9, .34f, 1.50f, 2, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, GRAB, SLAM, BITE, SCREECH)),
    DEACON     ("deacon",    38, 6, .40f, 1.00f, 1, 7, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE)),
    BOILER     ("boiler",    50, 6, .28f, 1.25f, 1, 0, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, TAIL_SWEEP, BITE, SPIT)),
    PROTOMORPH ("protomorph",55, 9, .38f, 1.15f, 1, 1, false, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, POUNCE)),
    STALKER    ("stalker",   45, 8, .46f, 1.05f, 1, 4, true,  false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE)),
    MATRIARCH  ("matriarch", 320, 18, 0.26f, 2.3f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY)),
    EMPRESS    ("empress", 400, 20, 0.25f, 2.5f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT)),
    QUEEN_MOTHER ("queen_mother", 600, 24, 0.24f, 3.0f, 2, 2, true, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT)),
    KING       ("king", 300, 22, 0.28f, 2.2f, 2, 3, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, SLAM, SCREECH, CHARGE)),
    CHARGER    ("charger", 90, 11, 0.45f, 1.3f, 0, 3, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, CHARGE, POUNCE)),
    BRUTE      ("brute", 110, 13, 0.3f, 1.5f, 1, 5, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, SLAM, CHARGE, TAIL_SWEEP, BITE, GRAB)),
    PATHOGEN_RUNNER ("pathogen_runner", 24, 4, 0.52f, 0.85f, 0, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, POUNCE)),
    PATHOGEN_BLIGHT ("pathogen_blight", 40, 5, 0.34f, 1.05f, 1, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, SPIT)),
    PATHOGEN_BRUTE ("pathogen_brute", 140, 15, 0.27f, 1.6f, 1, 6, false, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, GRAB, BITE)),
    PATHOGEN_QUEEN ("pathogen_queen", 280, 17, 0.26f, 2.1f, 2, 6, false, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, GRAB, IMPALE, SLAM, SCREECH, LAY, SPIT)),
    BURSTER    ("burster", 30, 6, 0.4f, 0.9f, 0, 0, false, false, false, EnumSet.of(CLAW_L, CLAW_R, BITE, POUNCE)),
    PROWLER    ("prowler", 42, 8, 0.44f, 1.0f, 1, 4, true, false, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, POUNCE)),
    BULL_ALIEN ("bull_alien", 130, 13, 0.3f, 1.4f, 1, 3, true, false, false, EnumSet.of(CLAW_L, CLAW_R, SLAM, CHARGE, BITE, TAIL_SWEEP)),
    ARACHNID   ("arachnid", 60, 8, 0.4f, 1.2f, 0, 0, false, true, false, EnumSet.of(CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, BITE, GRAB, POUNCE));

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
}
