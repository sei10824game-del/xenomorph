package com.xenomorph;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/xenomorph-common.toml */
public final class XenoConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue HIVE_RARITY;
    public static final ForgeConfigSpec.BooleanValue ACID_DAMAGES_ARMOR, ENABLE_CEILING_AMBUSH;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HIVE_RARITY = b.comment("A newly generated overworld chunk holds a hive with probability 1/N. Higher = rarer.").defineInRange("hiveRarity", 90, 5, 5000);
        ACID_DAMAGES_ARMOR = b.comment("Xeno acid blood eats the armor of whoever hit them.").define("acidDamagesArmor", true);
        ENABLE_CEILING_AMBUSH = b.comment("Xenos may hang from cave ceilings and drop onto prey.").define("ceilingAmbush", true);
        DAMAGE_MULTIPLIER = b.comment("Global multiplier for xeno melee damage.").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        SPEC = b.build();
    }
    private XenoConfig() {}
}
