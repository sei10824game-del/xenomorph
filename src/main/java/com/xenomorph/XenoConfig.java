package com.xenomorph;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/xenomorph-common.toml */
public final class XenoConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue HIVE_RARITY;
    public static final ForgeConfigSpec.ConfigValue<String> PRESET;
    /** easy / normal / nightmare: xeno health, damage and hive frequency in one switch. */
    public static double hp() { return switch (PRESET.get().toLowerCase()) { case "easy" -> 0.7; case "nightmare" -> 1.5; default -> 1.0; }; }
    public static double dmg() { return DAMAGE_MULTIPLIER.get() * switch (PRESET.get().toLowerCase()) { case "easy" -> 0.6; case "nightmare" -> 1.4; default -> 1.0; }; }
    public static double hiveFreq() { return switch (PRESET.get().toLowerCase()) { case "easy" -> 0.5; case "nightmare" -> 2.0; default -> 1.0; }; }
    public static final ForgeConfigSpec.BooleanValue ACID_DAMAGES_ARMOR, ENABLE_CEILING_AMBUSH;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue FIGHT_OTHER_MONSTERS, HIVE_CREEP, HIVE_RAIDS, LABS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        PRESET = b.comment("Difficulty preset: easy (xeno hp x0.7, damage x0.6, half as many hives), normal, nightmare (hp x1.5, damage x1.4, twice as many hives).").define("preset", "normal");
        HIVE_RARITY = b.comment("A newly generated overworld chunk holds a hive with probability 1/N. Higher = rarer.").defineInRange("hiveRarity", 90, 5, 5000);
        ACID_DAMAGES_ARMOR = b.comment("Xeno acid blood eats the armor of whoever hit them.").define("acidDamagesArmor", true);
        ENABLE_CEILING_AMBUSH = b.comment("Xenos may hang from cave ceilings and drop onto prey.").define("ceilingAmbush", true);
        DAMAGE_MULTIPLIER = b.comment("Global multiplier for xeno melee damage.").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        FIGHT_OTHER_MONSTERS = b.comment("Xenos and all other hostile mobs (vanilla and other mods) attack each other. Turn off to leave other mods' monsters alone.").define("fightOtherMonsters", true);
        HIVE_CREEP = b.comment("Guarded hives slowly spread resin over nearby stone, dirt and wood (max ~40 blocks per hive). Turn off to leave terrain untouched.").define("hiveCreep", true);
        HIVE_RAIDS = b.comment("Guarded hives periodically send a raiding party at bosses and rival queens first, otherwise the nearest settlement or creature (villagers, golems, survival players, other mods mobs) 30-160 blocks away.").define("hiveRaids", true);
        LABS = b.comment("Rare ruined surface laboratories (Derelict Laboratory) with Mutagen and brewing supplies.").define("labs", true);
        SPEC = b.build();
    }
    private XenoConfig() {}
}
