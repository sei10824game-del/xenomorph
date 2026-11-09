package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;

public class XenoEntity extends Monster {
    private static final EntityDataAccessor<Boolean> BIPED = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.INT);

    public final Caste caste;
    public int attackTick;
    public float bipedAmt, bipedAmtO;          // client-side blended stance 0=quad 1=biped
    public final EnumMap<Attack, Integer> lockout = new EnumMap<>(Attack.class);
    public int cooldown, stanceTimer, layTimer = 600;
    private LivingEntity held;
    private boolean impaled;
    private double vx, vz;                      // frozen lunge direction

    public XenoEntity(EntityType<? extends XenoEntity> type, Level level, Caste caste) {
        super(type, level);
        this.caste = caste;
        this.xpReward = (int) (caste.hp / 4);
        this.setMaxUpStep(1.1f);
        if (caste.stance == 2) entityData.set(BIPED, true);
    }

    public static AttributeSupplier.Builder attributes(Caste c) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, c.hp)
                .add(Attributes.ATTACK_DAMAGE, c.dmg)
                .add(Attributes.MOVEMENT_SPEED, c.speed)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, Math.min(0.9, 0.1 + c.scale * 0.3))
                .add(Attributes.ARMOR, 4 + c.scale * 4);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BIPED, false);
        entityData.define(ATTACK, 0);
    }
    public boolean isBiped() { return entityData.get(BIPED); }
    public Attack attack() { return Attack.values()[entityData.get(ATTACK)]; }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ATTACK) attackTick = 0;
        if (key == BIPED) refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions d = super.getDimensions(pose);
        return isBiped() ? d : EntityDimensions.scalable(d.width, d.height * 0.55f);
    }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions dim) { return dim.height * 0.82f; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new XenoAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, FacehuggerEntity.class, ChestbursterEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
        // enemy faction xenos and every other hostile mob (husks, mutant zombies, other mods' monsters)
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> !friendly(e) && (faction(e) != 0 || e instanceof net.minecraft.world.entity.monster.Enemy)));
    }

    /** 0 = not a xeno, 1 = classic hive, 2 = pathogen. Different non-zero factions fight each other. */
    public static int faction(Entity e) {
        if (e instanceof XenoEntity x) return x.caste.id.startsWith("pathogen") ? 2 : 1;
        return isXeno(e) ? 1 : 0;
    }
    public boolean friendly(Entity e) { return faction(e) == faction(this); }

    public static boolean isXeno(Entity e) {
        return e instanceof XenoEntity || e instanceof FacehuggerEntity || e instanceof ChestbursterEntity || e instanceof XenoEggEntity;
    }

    // ---------- attack control ----------
    public void startAttack(Attack a) {
        entityData.set(ATTACK, a.ordinal());
        attackTick = 0; held = null; impaled = false;
        LivingEntity t = getTarget();
        if (t != null) { vx = t.getX() - getX(); vz = t.getZ() - getZ(); double l = Math.sqrt(vx * vx + vz * vz); if (l > 1e-4) { vx /= l; vz /= l; } }
        if (a == Attack.SCREECH) playSound(SoundEvents.ENDER_DRAGON_GROWL, 3f, 1.7f);
        else if (a != Attack.NONE && a != Attack.LAY) playSound(SoundEvents.SPIDER_AMBIENT, 1.2f, 0.55f + random.nextFloat() * 0.2f);
    }
    private void endAttack() {
        releaseHeld(false);
        Attack a = attack();
        int lock = switch (a) { case POUNCE -> 80; case SPIT -> 50; case SCREECH -> 260; case GRAB -> 120; case CHARGE -> 140; case IMPALE -> 120; case SLAM -> 60; case TAIL_SWEEP -> 50; default -> 0; };
        if (lock > 0) lockout.put(a, lock);
        entityData.set(ATTACK, 0);
        cooldown = 8 + random.nextInt(14);
    }
    public boolean ready(Attack a) { return lockout.getOrDefault(a, 0) <= 0; }
    public double reach() { return 2.6 * Math.max(1, caste.scale * 0.9); }

    @Override public void tick() {
        super.tick();
        if (attack() != Attack.NONE) attackTick++;
        if (level().isClientSide) {
            bipedAmtO = bipedAmt;
            float goal = isBiped() ? 1 : 0;
            bipedAmt += Mth.clamp(goal - bipedAmt, -0.06f, 0.06f);
            return;
        }
        if (cooldown > 0) cooldown--;
        lockout.replaceAll((k, v) -> v - 1);
        lockout.values().removeIf(v -> v <= 0);
        updateStance();
        Attack a = attack();
        if (a != Attack.NONE) {
            runAttack(a);
            if (attackTick >= a.duration) endAttack();
        } else if (caste.attacks.contains(Attack.LAY) && getTarget() == null && --layTimer <= 0 && getNavigation().isDone()) {
            layTimer = 900 + random.nextInt(600);
            if (level().getEntitiesOfClass(XenoEggEntity.class, getBoundingBox().inflate(16)).size() < 5) startAttack(Attack.LAY);
        }
    }

    private void updateStance() {
        if (caste.stance != 1) { if (isBiped() != (caste.stance == 2)) entityData.set(BIPED, caste.stance == 2); return; }
        if (stanceTimer > 0) { stanceTimer--; return; }
        LivingEntity t = getTarget();
        boolean wantBiped;
        if (attack() != Attack.NONE) wantBiped = attack() != Attack.POUNCE && attack() != Attack.CHARGE;
        else if (t == null) wantBiped = getDeltaMovement().horizontalDistanceSqr() < 0.004;
        else wantBiped = distanceToSqr(t) < 36;
        if (wantBiped != isBiped()) { entityData.set(BIPED, wantBiped); stanceTimer = 24; }
    }

    // ---------- attack effects ----------
    private LivingEntity tgt() { return getTarget(); }
    private Vec3 fwd() { return new Vec3(-Mth.sin(yBodyRot * Mth.DEG_TO_RAD), 0, Mth.cos(yBodyRot * Mth.DEG_TO_RAD)); }

    private boolean strike(LivingEntity t, float mult, double reach, double kb) {
        if (t == null || !t.isAlive() || friendly(t)) return false;
        if (distanceTo(t) > reach + t.getBbWidth() * 0.5) return false;
        boolean ok = t.hurt(damageSources().mobAttack(this), caste.dmg * mult);
        if (ok && kb > 0) { Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize(); t.push(d.x * kb, 0.25 * kb, d.z * kb); t.hurtMarked = true; }
        return ok;
    }
    private List<LivingEntity> around(double r) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1.0, r), e -> e != this && !friendly(e) && e.isAlive() && distanceTo(e) <= r + e.getBbWidth() / 2);
    }
    private void faceTarget(float lerp) {
        LivingEntity t = tgt();
        if (t == null) return;
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float y = Mth.rotLerp(lerp, getYRot(), yaw);
        setYRot(y); setYBodyRot(y); setYHeadRot(y);
        getLookControl().setLookAt(t, 60, 60);
    }

    private void runAttack(Attack a) {
        int t = attackTick;
        if (a != Attack.CHARGE && a != Attack.LAY) getNavigation().stop();
        boolean locked = (a == Attack.POUNCE && t >= a.hit) || (a == Attack.CHARGE && t > a.hit);
        if (!locked) faceTarget(a == Attack.TAIL_SWEEP && t > a.hit ? 0f : 0.45f);
        LivingEntity target = tgt();
        double r = reach();
        switch (a) {
            case CLAW_L, CLAW_R -> { if (t == a.hit) { Vec3 f = fwd(); if (inFront(target, f)) strike(target, 1f, r, 0.5); } }
            case DOUBLE_CLAW -> { if (t == a.hit || t == a.hit + 6) if (inFront(target, fwd())) strike(target, 0.85f, r, 0.35); }
            case BITE -> { if (t == a.hit && inFront(target, fwd())) { if (strike(target, 1.5f, r, 0.1)) heal(1f); } }
            case TAIL_STAB -> {
                if (t == a.hit - 2) playSound(SoundEvents.TRIDENT_THROW, 1f, 0.6f);
                if (t == a.hit && target != null && strike(target, 1.3f, 5.5 * Math.max(1, caste.scale * 0.8), 0.7))
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            }
            case TAIL_SWEEP -> {
                if (t >= a.hit - 1 && t <= a.hit + 5 && t % 2 == 0) {
                    double rad = 3.6 * Math.max(1, caste.scale * 0.85);
            for (LivingEntity e : around(rad)) if (!sweptAlready(e)) { strike(e, 0.9f, rad, 1.3); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0)); markSwept(e); }
                }
                if (t == a.hit) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
            }
            case GRAB -> tickGrab(a, target);
            case IMPALE -> tickImpale(a, target);
            case POUNCE -> {
                if (t == a.hit) { setDeltaMovement(vx * 1.15, 0.55, vz * 1.15); hasImpulse = true; playSound(SoundEvents.RAVAGER_ROAR, 1.2f, 1.4f); }
                else if (t > a.hit && t < a.hit + 14 && target != null && getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
                    if (strike(target, 1.3f, 4, 0.6)) { target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2)); attackTick = Math.max(attackTick, a.hit + 14); }
                }
            }
            case SPIT -> {
                if (t == a.hit && target != null) {
                    AcidSpit s = new AcidSpit(level(), this, caste.dmg * 0.8f);
                    Vec3 e = position().add(0, getEyeHeight(), 0).add(fwd().scale(0.8 * caste.scale));
                    s.setPos(e);
                    double dx = target.getX() - e.x, dy = target.getEyeY() - e.y - 0.1, dz = target.getZ() - e.z;
                    s.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.05, dz, 1.5f, 1.5f);
                    level().addFreshEntity(s);
                    playSound(SoundEvents.LLAMA_SPIT, 1.5f, 0.5f);
                }
            }
            case SCREECH -> {
                if (t == a.hit) {
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 1.9f);
                    for (LivingEntity e : around(14)) if (e instanceof Player || e instanceof AbstractVillager) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    }
                    for (XenoEntity x : level().getEntitiesOfClass(XenoEntity.class, getBoundingBox().inflate(24))) {
                        x.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                        x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                        if (x.getTarget() == null && target != null) x.setTarget(target);
                    }
                }
            }
            case CHARGE -> {
                if (t > a.hit) {
                    setDeltaMovement(vx * 0.9, getDeltaMovement().y, vz * 0.9);
                    for (LivingEntity e : around(1.6 * caste.scale)) if (strike(e, 1.4f, 3, 1.8)) { e.hurtMarked = true; }
                    if (horizontalCollision && t > a.hit + 4) { attackTick = a.duration - 8; playSound(SoundEvents.GENERIC_EXPLODE, 1f, 0.7f); addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3)); }
                }
            }
            case SLAM -> {
                if (t == a.hit) {
                    playSound(SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
                    double rad = 4.2 * Math.max(1, caste.scale * 0.7);
                    for (LivingEntity e : around(rad)) { strike(e, 1.2f, rad, 1.0); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1)); }
                    if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.EXPLOSION, getX() + fwd().x * 2, getY() + 0.1, getZ() + fwd().z * 2, 3, 0.8, 0.1, 0.8, 0);
                }
            }
            case LAY -> {
                if (t == a.hit && level() instanceof ServerLevel sl) {
                    XenoEggEntity egg = XenomorphMod.EGG.get().create(sl);
                    if (egg != null) { Vec3 p = position().subtract(fwd().scale(2.2)); egg.moveTo(p.x, getY(), p.z, random.nextFloat() * 360, 0); sl.addFreshEntity(egg); }
                }
            }
            default -> {}
        }
    }
    private final java.util.Set<Integer> sweptIds = new java.util.HashSet<>();
    private boolean sweptAlready(LivingEntity e) { return sweptIds.contains(e.getId()); }
    private void markSwept(LivingEntity e) { sweptIds.add(e.getId()); }

    private boolean inFront(LivingEntity t, Vec3 f) {
        if (t == null) return false;
        Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 || d.normalize().dot(f) > 0.15;
    }

    // Seize -> lift -> inner jaw execution -> throw
    private void tickGrab(Attack a, LivingEntity target) {
        int t = attackTick;
        if (t == a.hit && held == null && target != null && !friendly(target) && distanceTo(target) <= reach() + 1.0) {
            held = target; playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.5f);
            held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5));
        }
        if (held == null) { if (t > a.hit + 2) attackTick = a.duration; return; }
        if (!held.isAlive()) { held = null; attackTick = a.duration; return; }
        double lift = Mth.clamp((t - a.hit) / 10.0, 0, 1);
        Vec3 f = fwd();
        Vec3 p = position().add(f.scale(0.9 * caste.scale + 0.3)).add(0, (getBbHeight() * 0.62 + 0.2) * lift - held.getBbHeight() * 0.35 * lift * 0.5, 0);
        hold(p);
        if (t == 30 || t == 42) {
            playSound(SoundEvents.RAVAGER_ATTACK, 1.5f, 0.6f);
            held.hurt(damageSources().mobAttack(this), caste.dmg * 1.6f);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, held.getX(), held.getEyeY(), held.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
        if (t == a.duration - 6) releaseHeld(true);
    }
    // Tail pierces the victim, hoists it overhead, then flings it
    private void tickImpale(Attack a, LivingEntity target) {
        int t = attackTick;
        if (t == a.hit && held == null && target != null && !friendly(target) && distanceTo(target) <= 7) {
            held = target; impaled = true;
            held.hurt(damageSources().mobAttack(this), caste.dmg * 1.8f);
            playSound(SoundEvents.TRIDENT_HIT, 1.5f, 0.5f);
        }
        if (held == null) { if (t > a.hit + 2) attackTick = a.duration; return; }
        if (!held.isAlive()) { held = null; return; }
        double lift = Mth.clamp((t - a.hit) / 8.0, 0, 1);
        Vec3 f = fwd();
        hold(position().add(f.scale(0.6)).add(0, getBbHeight() * (0.9 + 0.35 * lift), 0));
        if (t == a.hit + 10) held.hurt(damageSources().mobAttack(this), caste.dmg * 0.8f);
        if (t == a.duration - 4) releaseHeld(true);
    }
    private void hold(Vec3 p) {
        held.fallDistance = 0;
        held.setDeltaMovement(Vec3.ZERO);
        held.teleportTo(p.x, p.y, p.z);
        held.hurtMarked = true;
    }
    private void releaseHeld(boolean fling) {
        if (held != null && held.isAlive() && fling) {
            Vec3 f = fwd();
            held.setDeltaMovement(f.x * 1.3, 0.5, f.z * 1.3); held.hurtMarked = true;
        }
        held = null; impaled = false; sweptIds.clear();
    }

    // ---------- acid blood ----------
    @Override public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && src.getEntity() instanceof LivingEntity att && !friendly(att) && distanceTo(att) < 3.5 && random.nextFloat() < 0.6f) acidBurn(att, 1 + random.nextInt(2));
        return r;
    }
    private void acidBurn(LivingEntity e, float dmg) {
        e.hurt(damageSources().magic(), dmg);
        playSound(SoundEvents.LAVA_EXTINGUISH, 0.7f, 1.6f);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
        for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (s.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack st = e.getItemBySlot(s);
            if (!st.isEmpty() && random.nextFloat() < 0.5f) st.hurtAndBreak(4, e, x -> x.broadcastBreakEvent(s));
        }
    }
    @Override public void die(DamageSource src) {
        super.die(src);
        releaseHeld(false);
        if (!level().isClientSide) {
            boolean big = caste == Caste.BOILER || caste == Caste.BURSTER;
            for (LivingEntity e : around(big ? 5.0 : 2.6)) acidBurn(e, big ? 6 : 3);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.5, getZ(), 40, 0.8, 0.4, 0.8, 0.1);
        }
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override public float getVoicePitch() { return 0.6f / Math.max(0.7f, caste.scale * 0.6f) + 0.2f; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) { playSound(SoundEvents.SPIDER_STEP, 0.25f, 0.6f); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Biped", isBiped()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Biped") && caste.stance == 1) entityData.set(BIPED, tag.getBoolean("Biped")); }
}
