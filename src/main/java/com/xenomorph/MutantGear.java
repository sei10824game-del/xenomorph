package com.xenomorph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Trophies of the mutant xenomorphs, mirroring the Mutant Monsters rewards: Hulk Hammer (ground slam) and Void Claw (blink). */
public final class MutantGear {
    private MutantGear() {}

    /** Right-click: slam the ground, hurling every nearby creature skyward and away. */
    public static class HulkHammer extends Item {
        public HulkHammer() { super(new Item.Properties().durability(250)); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (level instanceof ServerLevel sl) {
                sl.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 0.7f);
                sl.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 4, 1.5, 0.1, 1.5, 0);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5, 2, 5), e -> e != p && e.isAlive())) {
                    Vec3 d = e.position().subtract(p.position()).multiply(1, 0, 1); d = d.lengthSqr() > 1e-4 ? d.normalize() : new Vec3(0, 0, 1);
                    e.hurt(p.damageSources().playerAttack(p), 8f); e.setDeltaMovement(d.x * 0.9, 0.9, d.z * 0.9); e.hurtMarked = true;
                }
                st.hurtAndBreak(2, p, x -> x.broadcastBreakEvent(h));
            }
            p.getCooldowns().addCooldown(this, 40);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }

    /** Right-click: teleport up to 12 blocks along your line of sight. */
    public static class VoidClaw extends Item {
        public VoidClaw() { super(new Item.Properties().durability(120)); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (!level.isClientSide) {
                Vec3 from = p.getEyePosition(), to = from.add(p.getLookAngle().scale(12));
                var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                Vec3 dest = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation().subtract(p.getLookAngle().scale(0.6));
                p.teleportTo(dest.x, dest.y - p.getEyeHeight(), dest.z);
                level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
                p.fallDistance = 0;
                st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(h));
            }
            p.getCooldowns().addCooldown(this, 30);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }
}
