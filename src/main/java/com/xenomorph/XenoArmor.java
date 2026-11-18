package com.xenomorph;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Acid-neutralizing suit (the "Z-110 wardrobe" from Aliens: Genocide). Each piece cuts acid damage by a quarter; the full set is immune. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class XenoArmor {
    private XenoArmor() {}

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type t) { return new int[]{143, 208, 195, 169}[t.ordinal() % 4]; }
        @Override public int getDefenseForType(ArmorItem.Type t) { return new int[]{2, 5, 4, 2}[t.ordinal() % 4]; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(XenomorphMod.RESIN_ITEM.get()); }
        @Override public String getName() { return "xenomorph:acidproof"; }
        @Override public float getToughness() { return 0.5f; }
        @Override public float getKnockbackResistance() { return 0f; }
    };

    /** Mutant Skeleton armour: sturdier than iron; the full set shrugs off 40% of projectile damage. */
    public static final ArmorMaterial BONE = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type t) { return new int[]{231, 336, 315, 273}[t.ordinal() % 4]; }
        @Override public int getDefenseForType(ArmorItem.Type t) { return new int[]{3, 7, 5, 3}[t.ordinal() % 4]; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_CHAIN; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(XenomorphMod.FRAGMENT.get()); }
        @Override public String getName() { return "xenomorph:bone"; }
        @Override public float getToughness() { return 1f; }
        @Override public float getKnockbackResistance() { return 0.05f; }
    };
    public static int bonePieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot s : EquipmentSlot.values())
            if (s.getType() == EquipmentSlot.Type.ARMOR && e.getItemBySlot(s).getItem() instanceof ArmorItem a && a.getMaterial() == BONE) n++;
        return n;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && bonePieces(e.getEntity()) >= 4) e.setAmount(e.getAmount() * 0.6f);
    }

    public static int pieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot s : EquipmentSlot.values())
            if (s.getType() == EquipmentSlot.Type.ARMOR && e.getItemBySlot(s).getItem() instanceof ArmorItem a && a.getMaterial() == MATERIAL) n++;
        return n;
    }
}
