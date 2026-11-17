package com.xenomorph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Hold right-click to fire. Both wear out instead of needing ammo. */
public final class XenoWeapons {
    private XenoWeapons() {}

    public abstract static class Held extends Item {
        Held(int durability) { super(new Item.Properties().durability(durability)); }
        @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.BOW; }
        @Override public int getUseDuration(ItemStack s) { return 72000; }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) { p.startUsingItem(h); return InteractionResultHolder.consume(p.getItemInHand(h)); }
    }

    /** M41A-style pulse rifle: hitscan bursts, 7 damage a round. */
    public static class PulseRifle extends Held {
        public PulseRifle() { super(600); }
        @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            int t = getUseDuration(stack) - remaining;
            if (level.isClientSide || t % 3 != 0 || !(user instanceof Player p)) return;
            Vec3 from = p.getEyePosition(), look = p.getLookAngle();
            BlockHitResult bh = level.clip(new ClipContext(from, from.add(look.scale(48)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            double d = bh.getLocation().distanceTo(from);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, from, from.add(look.scale(d)), p.getBoundingBox().expandTowards(look.scale(d)).inflate(1), e -> !e.isSpectator() && e.isPickable());
            if (eh != null && eh.getEntity() instanceof LivingEntity target) { target.hurt(level.damageSources().playerAttack(p), 7f); d = eh.getLocation().distanceTo(from); }
            ServerLevel sl = (ServerLevel) level;
            for (double i = 1.5; i < d; i += 1.2) sl.sendParticles(ParticleTypes.CRIT, from.x + look.x * i, from.y + look.y * i - 0.15, from.z + look.z * i, 1, 0, 0, 0, 0);
            level.playSound(null, p.blockPosition(), SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 0.8f, 1.9f);
            stack.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(p.getUsedItemHand()));
        }
    }

    /** Incinerator unit: a short fire cone. Xenomorphs burn. */
    public static class Flamethrower extends Held {
        public Flamethrower() { super(400); }
        @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            int t = getUseDuration(stack) - remaining;
            if (level.isClientSide || !(user instanceof Player p)) return;
            ServerLevel sl = (ServerLevel) level;
            Vec3 from = p.getEyePosition(), look = p.getLookAngle();
            for (int i = 1; i <= 7; i++) sl.sendParticles(ParticleTypes.FLAME, from.x + look.x * i, from.y + look.y * i - 0.2, from.z + look.z * i, 2, 0.12 * i, 0.12 * i, 0.12 * i, 0.01);
            if (t % 4 == 0) {
                level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.8f);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), e -> e != p && e.isAlive())) {
                    Vec3 d = e.getEyePosition().subtract(from);
                    if (d.length() < 8 && d.normalize().dot(look) > 0.9) { e.setSecondsOnFire(5); e.hurt(level.damageSources().inFire(), 2f); }
                }
                stack.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(p.getUsedItemHand()));
            }
        }
    }
}
