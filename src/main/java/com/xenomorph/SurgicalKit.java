package com.xenomorph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Cuts a xenomorph embryo out of its host (costs the host health). Right-click someone, or hold right-click to operate on yourself. */
public class SurgicalKit extends Item {
    public SurgicalKit() { super(new Item.Properties().durability(6)); }

    public static boolean implanted(LivingEntity e) { return e.hasEffect(XenomorphMod.EMBRYO.get()) || e.hasEffect(XenomorphMod.RED_EMBRYO.get()); }

    static boolean remove(LivingEntity host, Player surgeon) {
        if (!implanted(host)) return false;
        host.removeEffect(XenomorphMod.EMBRYO.get()); host.removeEffect(XenomorphMod.RED_EMBRYO.get());
        if (host instanceof Mob m && m.isNoAi()) m.setNoAi(false);
        float cost = Math.min(6f, Math.max(0f, host.getHealth() - 1f));     // never kills the patient
        if (cost > 0 && !(host instanceof Player p && p.isCreative())) host.hurt(host.damageSources().generic(), cost);
        if (host.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            sl.playSound(null, host.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1f, 0.6f);
        }
        return true;
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player p, LivingEntity target, InteractionHand hand) {
        if (p.level().isClientSide) return implanted(target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!remove(target, p)) return InteractionResult.PASS;
        stack.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(hand));
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        if (!implanted(p)) { if (!l.isClientSide) p.displayClientMessage(Component.literal("No embryo detected."), true); return InteractionResultHolder.pass(p.getItemInHand(h)); }
        p.startUsingItem(h);
        return InteractionResultHolder.consume(p.getItemInHand(h));
    }
    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack s) { return 60; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level l, LivingEntity user) {
        if (!l.isClientSide && user instanceof Player p && remove(p, p)) stack.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(p.getUsedItemHand()));
        return stack;
    }
}
