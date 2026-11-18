package com.xenomorph.entity;

import com.xenomorph.XenoConfig;
import com.xenomorph.XenomorphMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server logic for the mutant lineage (Hulk / Bone / Blast / Void / Frost xenos). Each attack is the xenomorph take on a Mutant Monsters
 * mutant ability: Mutant Zombie (seismic slam, throw, roar), Mutant Skeleton (arrows, multi-shot, rib crush), Mutant Creeper (chain
 * explosions, lightning, minions), Mutant Enderman (teleport, clones, tele-smash, scream, block throw) and Mutant Snow Golem (ice chunks).
 */
final class MutantAttacks {
    private MutantAttacks() {}

    static int lock(Attack a) {
        return switch (a) { case SEISMIC -> 70; case HURL -> 160; case ROAR_BLAST -> 400; case BONE_SHOT -> 40; case BONE_BARRAGE -> 160; case RIB_CRUSH -> 80; case BLAST_CHARGE -> 300;
            case MINION_BURST -> 260; case TELE_SMASH -> 140; case THROW_BLOCK -> 60; case CLONE_SPLIT -> 600; default -> 0; };
    }

    static void run(XenoEntity x, Attack a, int t, LivingEntity target, double r) {
        if (!(x.level() instanceof ServerLevel sl)) return;
        switch (a) {
            case SEISMIC -> {                                                          // a line of shockwaves racing 8-11 blocks forward, one block per tick
                if (t == a.hit) x.clearSwept();
                if (t >= a.hit && t < a.hit + 11) {
                    int d = t - a.hit + 1;
                    Vec3 f = x.fwd(), p = x.position().add(f.scale(d + 1.0));
                    sl.sendParticles(ParticleTypes.POOF, p.x, x.getY() + 0.1, p.z, 3, 0.6, 0.05, 0.6, 0.02);
                    BlockPos b = BlockPos.containing(p.x, x.getY() - 0.5, p.z);
                    sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(b)), p.x, x.getY() + 0.2, p.z, 10, 0.5, 0.2, 0.5, 0.1);
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p.x - 1.5, x.getY() - 1, p.z - 1.5, p.x + 1.5, x.getY() + 2.5, p.z + 1.5), e -> e != x && !x.friendly(e) && e.isAlive()))
                        if (!x.sweptHit(e)) { x.markSwept(e); x.strike(e, 0.9f, 99, 0.2); e.setDeltaMovement(e.getDeltaMovement().add(0, 0.75, 0)); e.hurtMarked = true; }
                    if (d % 3 == 0) x.playSound(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.8f + d * 0.04f);
                }
            }
            case HURL -> {                                                             // fling prey up at once, then dive onto it
                if (target == null) return;
                Vec3 d = target.position().subtract(x.position()).multiply(1, 0, 1);
                Vec3 dir = d.lengthSqr() > 1e-4 ? d.normalize() : x.fwd();
                if (t == 4 && x.distanceTo(target) < 5) { target.stopRiding(); target.setDeltaMovement(dir.x * 0.8, 1.6, dir.z * 0.8); target.hurtMarked = true; x.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.5f); }
                if (t == a.hit) { Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(x.position().add(0, x.getBbHeight() * 0.5, 0)).normalize(); x.setDeltaMovement(to.x * 1.5, Math.max(0.5, to.y * 1.1 + 0.3), to.z * 1.5); x.hasImpulse = true; }
                if (t >= a.hit && t <= a.hit + 18 && x.distanceTo(target) < 2.8 + target.getBbWidth() && x.strike(target, 1.4f, 4, 0.2)) {
                    target.setDeltaMovement(dir.x * 0.6, -1.2, dir.z * 0.6); target.hurtMarked = true; x.playSound(SoundEvents.GENERIC_EXPLODE, 1f, 0.7f);
                    sl.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 2, 0.3, 0.1, 0.3, 0);
                    x.attackTick = a.hit + 19;                                          // landed: wrap up
                }
            }
            case ROAR_BLAST -> {                                                       // Zombie roar (12 blocks) / Enderman scream (20 blocks)
                boolean scream = x.caste == Caste.VOID_XENO;
                double rad = scream ? 20 : 12;
                if (t == a.hit) {
                    x.playSound(XenomorphMod.SCREECH.get(), 4f, scream ? 0.6f : 0.8f);
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, x.getBoundingBox().inflate(rad, rad * 0.6, rad), e -> e != x && !x.friendly(e) && e.isAlive() && x.distanceToSqr(e) <= rad * rad)) {
                        Vec3 d = e.position().subtract(x.position()).multiply(1, 0, 1); d = d.lengthSqr() > 1e-4 ? d.normalize() : x.fwd();
                        e.hurt(x.damageSources().magic(), (scream ? 4f : 2f + x.getRandom().nextInt(2)) * (float) XenoConfig.dmg());
                        e.setDeltaMovement(d.x * 0.7, 0.3, d.z * 0.7); e.hurtMarked = true;
                    }
                    sl.sendParticles(ParticleTypes.SONIC_BOOM, x.getX(), x.getY() + x.getBbHeight() * 0.7, x.getZ(), 1, 0, 0, 0, 0);
                }
                if (!scream && (t == 24 || t == 40) && sl.getEntitiesOfClass(XenoEntity.class, x.getBoundingBox().inflate(24)).size() < 14) {   // the Mutant Zombie's roar raises its dead: drones claw out of the ground
                    for (int i = 0; i < 2; i++) {
                        XenoEntity m = XenomorphMod.XENOS.get(Caste.DRONE).get().create(sl);
                        if (m == null) continue;
                        double an = x.getRandom().nextDouble() * Math.PI * 2, rr = 3 + x.getRandom().nextDouble() * 4;
                        m.moveTo(x.getX() + Math.cos(an) * rr, x.getY(), x.getZ() + Math.sin(an) * rr, x.getRandom().nextFloat() * 360, 0);
                        if (!sl.noCollision(m)) continue;
                        if (target != null) m.setTarget(target);
                        sl.addFreshEntity(m); sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY(), m.getZ(), 10, 0.4, 0.2, 0.4, 0.05);
                    }
                }
            }
            case BONE_SHOT -> {
                if (t == 5) x.playSound(SoundEvents.CROSSBOW_QUICK_CHARGE_2, 1f, 1f);
                if (t == a.hit - 2) x.playSound(SoundEvents.CROSSBOW_LOADING_END, 1f, 1.2f);
                if (t == a.hit && target != null) {
                    Arrow ar = arrow(x, target, 2.4f, 1f);
                    if (x.getRandom().nextInt(4) == 0) ar.addEffect(new MobEffectInstance(MobEffects.POISON, 80 + x.getRandom().nextInt(60), 0));
                    if (x.getRandom().nextInt(4) == 0) ar.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120 + x.getRandom().nextInt(60), 1));
                    if (x.getRandom().nextInt(4) == 0) ar.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120 + x.getRandom().nextInt(60), 1));
                    sl.addFreshEntity(ar); x.playSound(SoundEvents.CROSSBOW_SHOOT, 1f, 0.8f);
                }
            }
            case BONE_BARRAGE -> {
                if (t == 10 && target != null) {                                       // backflip away (or sideways when the prey is far)
                    Vec3 d = target.position().subtract(x.position()).multiply(1, 0, 1); d = d.lengthSqr() > 1e-4 ? d.normalize() : x.fwd();
                    double k = x.distanceTo(target) < 4 ? -0.8 : -0.25;
                    x.setDeltaMovement(d.x * k, 0.9, d.z * k); x.hasImpulse = true;
                }
                if (t >= a.hit && t < a.hit + 4 && target != null) {
                    if (t == a.hit) { for (int i = 0; i < 6; i++) sl.addFreshEntity(arrow(x, target, 2f - x.getRandom().nextFloat() * 0.1f, 3f)); x.playSound(SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f); }
                }
            }
            case RIB_CRUSH -> {
                if (t == a.hit && target != null && x.strike(target, 1f + 6f / Math.max(1f, x.caste.dmg), r + 0.8, 0)) {
                    target.stopRiding();
                    target.setDeltaMovement((1 + x.getRandom().nextFloat() * 0.4) * (x.getRandom().nextBoolean() ? 1 : -1), 0.4 + x.getRandom().nextFloat() * 0.8, (1 + x.getRandom().nextFloat() * 0.4) * (x.getRandom().nextBoolean() ? 1 : -1));
                    target.hurtMarked = true; x.playSound(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.8f);
                }
            }
            case BLAST_CHARGE -> {                                                     // swell, four chain blasts, optional lightning that heals
                if (t < a.hit && t % 14 == 0) x.playSound(SoundEvents.CREEPER_PRIMED, 1f, 0.6f + t * 0.01f);
                if (t < a.hit) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x.getX(), x.getY() + x.getBbHeight() * 0.5, x.getZ(), 2, 0.4, 0.5, 0.4, 0.1);
                if (t >= a.hit && (t - a.hit) % 4 == 0 && (t - a.hit) / 4 < 4) {
                    double rad = 4.5 + x.getRandom().nextInt(2);
                    x.playSound(SoundEvents.GENERIC_EXPLODE, 2f, 0.8f + x.getRandom().nextFloat() * 0.3f);
                    sl.sendParticles(ParticleTypes.EXPLOSION, x.getX() + (x.getRandom().nextDouble() - 0.5) * 3, x.getY() + 0.8, x.getZ() + (x.getRandom().nextDouble() - 0.5) * 3, 2, 0.5, 0.3, 0.5, 0);
                    for (LivingEntity e : x.around(rad)) { x.strike(e, 0.8f, rad + 2, 1.2); }
                }
                if (t == a.hit + 14 && sl.canSeeSky(x.blockPosition()) && x.getRandom().nextInt(sl.isThundering() ? 2 : 4) == 0) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(sl);
                    if (bolt != null) { bolt.moveTo(x.getX(), x.getY(), x.getZ()); bolt.setVisualOnly(true); sl.addFreshEntity(bolt); x.heal(30f); }
                }
            }
            case MINION_BURST -> {
                if (t == a.hit && target != null) {
                    int n = (int) Math.ceil(x.getHealth() / x.getMaxHealth() * 4.0);
                    for (int i = 0; i < n; i++) {
                        XenoEntity m = XenomorphMod.XENOS.get(Caste.EXPLODER).get().create(sl);
                        if (m == null) continue;
                        m.moveTo(x.getX() + x.getRandom().nextFloat() - x.getRandom().nextFloat(), x.getY() + x.getRandom().nextFloat() * 0.5, x.getZ() + x.getRandom().nextFloat() - x.getRandom().nextFloat(), x.getYRot(), 0);
                        Vec3 d = target.position().subtract(m.position());
                        m.setDeltaMovement(d.x * 0.15 + x.getRandom().nextFloat() * 0.05, d.y * 0.15 + 0.3, d.z * 0.15 + x.getRandom().nextFloat() * 0.05);
                        m.setTarget(target); sl.addFreshEntity(m);
                    }
                    x.playSound(SoundEvents.CREEPER_HURT, 1.5f, 0.6f);
                }
            }
            case TELE_SMASH -> {
                if (target == null) return;
                if (t == a.hit - 1) {
                    Vec3 p = x.position().add(x.fwd().scale(2.0));
                    target.stopRiding(); target.teleportTo(p.x, x.getY(), p.z);
                    sl.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
                    x.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.6f);
                }
                if (t == a.hit) { target.hurt(x.damageSources().magic(), 6f * (float) XenoConfig.dmg()); x.strike(target, 1f, 5, 0.9); x.playSound(SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f); }
            }
            case THROW_BLOCK -> {
                if (t == a.hit && target != null) {
                    BlockState bs = x.caste == Caste.FROST_XENO ? (x.getRandom().nextInt(4) == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState())
                            : x.getRandom().nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    Vec3 p = x.position().add(x.fwd().scale(0.9 * x.caste.scale)).add(0, x.getBbHeight() * 0.8, 0);
                    FallingBlockEntity fb = FallingBlockEntity.fall(sl, BlockPos.containing(p), bs);     // lands as a block entity that hurts what it hits
                    fb.setPos(p.x, p.y, p.z); fb.setHurtsEntities(Math.max(2f, x.caste.dmg * 0.8f * (float) XenoConfig.dmg()), 60); fb.dropItem = false;
                    Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p);
                    double dist = d.length();
                    fb.setDeltaMovement(d.x / dist * 1.1, d.y / dist * 1.1 + dist * 0.025, d.z / dist * 1.1);
                    sl.addFreshEntity(fb);
                    x.playSound(SoundEvents.SNOWBALL_THROW, 1.2f, 0.5f);
                }
            }
            case CLONE_SPLIT -> {
                if (t == a.hit && sl.getEntitiesOfClass(XenoEntity.class, x.getBoundingBox().inflate(24), c -> c.cloneTicks > 0).size() < 4) {
                    for (int i = 0; i < 3; i++) {
                        XenoEntity c = XenomorphMod.XENOS.get(x.caste).get().create(sl);
                        if (c == null) continue;
                        double an = i * Math.PI * 2 / 3 + x.getRandom().nextDouble(), rr = 2.5;
                        c.moveTo(x.getX() + Math.cos(an) * rr, x.getY(), x.getZ() + Math.sin(an) * rr, x.getYRot(), 0);
                        if (!sl.noCollision(c)) continue;
                        c.makeClone(600);
                        if (target != null) c.setTarget(target);
                        sl.addFreshEntity(c); sl.sendParticles(ParticleTypes.PORTAL, c.getX(), c.getY() + 1, c.getZ(), 25, 0.3, 0.8, 0.3, 0.3);
                    }
                    x.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.4f);
                }
            }
            default -> {}
        }
    }

    private static Arrow arrow(XenoEntity x, LivingEntity target, float speed, float inaccuracy) {
        Arrow ar = new Arrow(x.level(), x);
        ar.setPos(x.getX() + x.fwd().x * 0.8, x.getY() + x.getBbHeight() * 0.75, x.getZ() + x.fwd().z * 0.8);
        double dx = target.getX() - ar.getX(), dy = target.getY(0.33) - ar.getY(), dz = target.getZ() - ar.getZ(), h = Math.sqrt(dx * dx + dz * dz);
        ar.shoot(dx, dy + h * 0.2, dz, speed, inaccuracy);
        ar.setBaseDamage(Math.max(2.0, x.caste.dmg * 0.7 * XenoConfig.dmg()));
        ar.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        return ar;
    }
}
