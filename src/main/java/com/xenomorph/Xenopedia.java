package com.xenomorph;

import com.xenomorph.entity.Attack;
import com.xenomorph.entity.XenoEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.stream.Collectors;

/** Right-click a xenomorph to read its field notes: durability, damage, speed, temper, stances, faction and attack list. */
public class Xenopedia extends Item {
    private static final String[] FACTIONS = {"-", "Classic hive", "Pathogen (black goo)", "Hunters", "Protomorph", "Deacon", "Neomorph", "Red strain", "Rogue King", "Clone hive", "White hybrids", "K-Series", "Newborn"};
    public Xenopedia() { super(new Item.Properties().stacksTo(1)); }

    @Override public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity e, InteractionHand h) {
        if (!(e instanceof XenoEntity x)) return InteractionResult.PASS;
        if (!p.level().isClientSide) {
            var c = x.caste;
            String stance = c.stance == 0 ? "quadruped" : c.stance == 2 ? "biped" : "quad/biped";
            String atk = c.attacks.stream().filter(a -> a != Attack.NONE).map(a -> a.name().toLowerCase().replace('_', ' ')).collect(Collectors.joining(", "));
            p.displayClientMessage(Component.literal("== ").append(x.getDisplayName()).append(" ==").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            p.displayClientMessage(Component.literal(String.format("HP %.0f | dmg %.0f | speed %.2f | scale %.1f | weight %.1f | temper %.2f | %s | %s", c.hp, c.dmg, c.speed, c.scale, c.weight(), c.ferocity(), stance, FACTIONS[Math.min(XenoEntity.faction(x), 12)])), false);
            p.displayClientMessage(Component.literal("Attacks: " + atk), false);
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }
}
