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
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;

public class XenoEntity extends Monster {
    private static final EntityDataAccessor<Boolean> BIPED = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMB = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HANG = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(XenoEntity.class, EntityDataSerializers.INT);

    public final Caste caste;
    public int attackTick;
    public float bipedAmt, bipedAmtO;          // client-side blended stance 0=quad 1=biped
    public final EnumMap<Attack, Integer> lockout = new EnumMap<>(Attack.class);
    public int cooldown, stanceTimer, layTimer = 600;
    private LivingEntity held;
    private boolean impaled;
    private double vx, vz, chargeMax;
    private Vec3 chargeFrom = Vec3.ZERO;                      // frozen lunge direction

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
        entityData.define(CLIMB, false);
        entityData.define(HANG, false);
    }
    public boolean isClimbing() { return entityData.get(CLIMB); }
    public boolean isHanging() { return entityData.get(HANG); }
    public boolean canHang() { return caste.attacks.contains(Attack.DROP_STRIKE); }
    private int hangTimer = 100, climbGrace;
    private boolean landed;
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level l) { return new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, l); }
    @Override public boolean onClimbable() { return isClimbing(); }
    @Override public boolean isImmobile() { return isHanging() || super.isImmobile(); }
    @Override public boolean causeFallDamage(float f, float m, DamageSource s) { return false; }

    /** Cling to a ceiling above (dark places only, called from idle AI). */
    public boolean tryHang() {
        if (level().isClientSide || isHanging() || !canHang() || isInWater()) return false;
        BlockPos base = blockPosition();
        double ceil = Double.NaN;
        for (int dy = 1; dy <= 7; dy++) {
            BlockPos c = base.above(dy); var st = level().getBlockState(c);
            if (st.isFaceSturdy(level(), c, net.minecraft.core.Direction.DOWN)) { ceil = c.getY(); break; }
            if (!st.getCollisionShape(level(), c).isEmpty()) return false;
        }
        if (Double.isNaN(ceil)) return false;
        boolean wasBiped = isBiped();
        entityData.set(BIPED, false); refreshDimensions();
        double ny = ceil - getBbHeight() - 0.02;
        if (ny < getY() || !level().noCollision(this, getBoundingBox().move(0, ny - getY(), 0))) { if (wasBiped) entityData.set(BIPED, true); return false; }
        setPos(getX(), ny, getZ()); setNoGravity(true); setDeltaMovement(Vec3.ZERO); entityData.set(HANG, true); getNavigation().stop();
        return true;
    }
    private void unhang() { entityData.set(HANG, false); setNoGravity(false); }

    /** Hanging: watch for prey, creep along the ceiling toward it, then drop. */
    private void tickHang() {
        setDeltaMovement(Vec3.ZERO);
        BlockPos above = BlockPos.containing(getX(), getBoundingBox().maxY + 0.05, getZ());
        if (!level().getBlockState(above).isFaceSturdy(level(), above, net.minecraft.core.Direction.DOWN)) { unhang(); return; }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || friendly(t)) {
            t = null; double best = 1e9;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14, 8, 14), e -> e != this && e.isAlive() && !friendly(e)
                    && (e instanceof Player p ? !p.isCreative() && !p.isSpectator() : e instanceof net.minecraft.world.entity.npc.AbstractVillager || faction(e) != 0 || e instanceof net.minecraft.world.entity.monster.Enemy))) {
                double d = distanceToSqr(e); if (d < best) { best = d; t = e; }
            }
            setTarget(t);
        }
        if (t == null) return;
        double dx = t.getX() - getX(), dz = t.getZ() - getZ(), hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(Mth.rotLerp(0.3f, getYRot(), yaw)); setYBodyRot(getYRot()); setYHeadRot(getYRot());
        if (hd < 4.8 && getY() - t.getY() < 9 && hasLineOfSight(t)) { startAttack(Attack.DROP_STRIKE); return; }
        if (hd < 24 && hd > 0.1) {
            Vec3[] steps = {new Vec3(dx / hd * 0.17, 0, dz / hd * 0.17), new Vec3(Math.signum(dx) * 0.17, 0, 0), new Vec3(0, 0, Math.signum(dz) * 0.17)};
            for (Vec3 st : steps) {
                BlockPos n = BlockPos.containing(getX() + st.x, getBoundingBox().maxY + 0.05, getZ() + st.z);
                if (level().noCollision(this, getBoundingBox().move(st)) && level().getBlockState(n).isFaceSturdy(level(), n, net.minecraft.core.Direction.DOWN)) { setPos(getX() + st.x, getY(), getZ() + st.z); break; }
            }
        }
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
        if (e instanceof XenoEntity x) return x.caste == Caste.PROTOMORPH ? 4 : x.caste.id.startsWith("pathogen") || x.caste == Caste.NEOMORPH || x.caste == Caste.NEWBORN || x.caste == Caste.DEACON ? 2 : 1;   // black-goo lineage vs classic hive
        if (e instanceof HunterEntity) return 3;
        return isXeno(e) ? 1 : 0;
    }
    /** Same hive, or a host carrying an embryo (the hive needs it alive until the chestburster emerges). */
    public boolean friendly(Entity e) { return faction(e) == faction(this) || (e instanceof LivingEntity l && l.hasEffect(XenomorphMod.EMBRYO.get())); }

    public static boolean isXeno(Entity e) {
        return e instanceof XenoEntity || e instanceof FacehuggerEntity || e instanceof ChestbursterEntity || e instanceof XenoEggEntity;
    }

    // ---------- attack control ----------
    public void startAttack(Attack a) {
        entityData.set(ATTACK, a.ordinal());
        attackTick = 0; held = null; impaled = false; landed = false;
        LivingEntity t = getTarget();
        if (t != null) { vx = t.getX() - getX(); vz = t.getZ() - getZ(); double l = Math.sqrt(vx * vx + vz * vz); if (l > 1e-4) { vx /= l; vz /= l; } }
        if (a == Attack.SCREECH) playSound(SoundEvents.ENDER_DRAGON_GROWL, 3f, 1.7f);
        else if (a != Attack.NONE && a != Attack.LAY) playSound(SoundEvents.SPIDER_AMBIENT, 1.2f, 0.55f + random.nextFloat() * 0.2f);
    }
    private void endAttack() {
        releaseHeld(false);
        Attack a = attack();
        int lock = switch (a) { case POUNCE -> 80; case SPIT -> 50; case SCREECH -> 260; case GRAB -> 120; case CHARGE -> 140; case IMPALE -> 120; case SLAM -> 60; case TAIL_SWEEP -> 50;
            case STOMP -> 100; case GRAB_SLAM -> 160; case SHADOW_STRIKE -> 90; case IMPREGNATE -> 220; case DEATH_ROLL -> 140; case LEAP_SLASH -> 70; case SPORE -> 200;
            case LOB -> 60; case VOLLEY -> 80; case VOMIT -> 100; case SUMMON -> 500; case FACE_TOSS -> 120; case WEB -> 140; case HORN_TOSS -> 80; case CREST_RAM -> 70;
            case FLAIL -> 90; case DROP_STRIKE -> 120; case FRENZY -> 120; case BLADE_FLURRY -> 100; case MULTI_STAB -> 80; default -> 0; };
        if (lock > 0) lockout.put(a, lock);
        entityData.set(ATTACK, 0);
        cooldown = (int) ((8 + random.nextInt(14)) * (1 - 0.7f * caste.ferocity()) * (0.7f + 0.3f * caste.weight()));
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
        if (getTarget() != null && friendly(getTarget())) setTarget(null);
        {
            if (getTarget() != null) climbGrace = 40; else if (climbGrace > 0) climbGrace--;     // brief target loss mid-wall must not drop us
            boolean cl = !isHanging() && horizontalCollision && caste.weight() < 1.5f && climbGrace > 0;
            if (cl != isClimbing()) entityData.set(CLIMB, cl);
        }
        if (isHanging() && attack() == Attack.NONE) tickHang();
        else if (!isHanging() && canHang() && getTarget() == null && attack() == Attack.NONE && --hangTimer <= 0) {
            hangTimer = 120 + random.nextInt(240);
            if (random.nextInt(3) == 0 && level().getMaxLocalRawBrightness(blockPosition()) < 8) tryHang();
        }
        updateStance();
        if (caste.ferocity() >= 0.5f && getHealth() < getMaxHealth() * 0.4f && !hasEffect(MobEffects.DAMAGE_BOOST)) {
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1)); addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            playSound(SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
        }
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
        if (isHanging()) return;
        if (caste.stance != 1) { if (isBiped() != (caste.stance == 2)) entityData.set(BIPED, caste.stance == 2); return; }
        if (stanceTimer > 0) { stanceTimer--; return; }
        LivingEntity t = getTarget();
        boolean wantBiped;
        if (attack() != Attack.NONE) wantBiped = switch (attack()) { case POUNCE, CHARGE, SHADOW_STRIKE, LEAP_SLASH, HORN_TOSS -> false; default -> true; };
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
        float wt = caste.weight();
        if (ok && kb > 0) { kb *= wt; Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize(); t.push(d.x * kb, 0.25 * kb, d.z * kb); t.hurtMarked = true; }
        if (ok && wt >= 1.4f && level() instanceof ServerLevel sl) {          // heavy hitters crack the ground and thud
            sl.sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + 0.1, t.getZ(), 6, 0.4, 0.1, 0.4, 0.05);
            playSound(SoundEvents.GENERIC_EXPLODE, 0.5f * wt, 1.5f - 0.4f * wt);
        }
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
        boolean locked = (a == Attack.POUNCE && t >= a.hit) || (a == Attack.CHARGE && t > a.hit) || (a == Attack.LEAP_SLASH && t >= 8) || (a == Attack.HORN_TOSS && t > 6);
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
                if (t == a.hit) {
                    if (target != null) { double dx = target.getX() - getX(), dz = target.getZ() - getZ(), l = Math.sqrt(dx * dx + dz * dz); if (l > 1e-3) { vx = dx / l; vz = dz / l; } else { vx = fwd().x; vz = fwd().z; } }
                    else { vx = fwd().x; vz = fwd().z; }
                    chargeFrom = position(); chargeMax = (target != null ? distanceTo(target) : 10) + 6;
                }
                if (t > a.hit) {
                    setDeltaMovement(vx * 0.8, getDeltaMovement().y, vz * 0.8);
                    boolean hitSomething = false;
                    for (LivingEntity e : around(1.6 * caste.scale)) if (strike(e, 1.4f, 3, 1.8)) { e.hurtMarked = true; hitSomething = true; }
                    double trav = position().distanceTo(chargeFrom);
                    boolean stalled = t > a.hit + 8 && trav < 1.0;                       // not actually moving: give up and resume the chase
                    if (hitSomething) { setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3)); attackTick = Math.max(attackTick, a.duration - 10); }
                    else if (trav > chargeMax || stalled) attackTick = Math.max(attackTick, a.duration - 6);       // reached the spot (or stalled): stop rushing
                    else if (horizontalCollision && t > a.hit + 4) { attackTick = Math.max(attackTick, a.duration - 8); playSound(SoundEvents.GENERIC_EXPLODE, 1f, 0.7f); addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3)); }
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
            case BLADE_FLURRY -> {
                if (t >= a.hit && t <= a.hit + 20 && (t - a.hit) % 4 == 0) {
                    for (LivingEntity e : around(r + 0.6)) strike(e, 0.7f, r + 0.6, 0.35);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.4f); setDeltaMovement(getDeltaMovement().add(fwd().scale(0.12)));
                }
            }
            case FRENZY -> {
                if (t >= a.hit && t <= a.hit + 28 && (t - a.hit) % 4 == 0) {
                    if (strike(target, 0.65f, r, 0.2) && ((t - a.hit) / 4) % 2 == 1) heal(1f);
                    setDeltaMovement(getDeltaMovement().add(fwd().scale(0.1)));
                }
            }
            case HORN_TOSS -> {
                if (t > 6 && t < a.hit) setDeltaMovement(fwd().x * 0.4, getDeltaMovement().y, fwd().z * 0.4);
                if (t == a.hit) {
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.5f, 0.6f);
                    for (LivingEntity e : around(r + 0.8)) if (inFront(e, fwd()) && strike(e, 1.4f, r + 0.8, 0.4)) launch(e, 0.85 + 0.15 * caste.weight(), 0.3);
                }
            }
            case STOMP -> {
                if (t == a.hit) { playSound(SoundEvents.GENERIC_EXPLODE, 2f, 0.5f); sweptIds.clear(); }
                if (t >= a.hit && t <= a.hit + 8) {
                    double rad = 1.2 + (t - a.hit) * 0.8 * Math.max(1, caste.scale * 0.6);
                    if (level() instanceof ServerLevel sl) for (int i = 0; i < 16; i++) { double an = i * Math.PI / 8; sl.sendParticles(ParticleTypes.POOF, getX() + Math.cos(an) * rad, getY() + 0.1, getZ() + Math.sin(an) * rad, 1, 0.1, 0.05, 0.1, 0.02); }
                    for (LivingEntity e : around(rad + 0.8)) {
                        double dd = Math.hypot(e.getX() - getX(), e.getZ() - getZ());
                        if (dd >= rad - 1.0 && !sweptAlready(e) && strike(e, 1.0f, 20, 0.5)) { markSwept(e); launch(e, 0.55, 0); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1)); }
                    }
                }
            }
            case GRAB_SLAM -> tickGrabSlam(a, target);
            case SHADOW_STRIKE -> {
                if (t == 1) addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 24, 0, false, false));
                if (t == 7 && target != null) {
                    Vec3 look = target.getLookAngle().multiply(1, 0, 1);
                    if (look.lengthSqr() > 1e-4) {
                        Vec3 np = target.position().subtract(look.normalize().scale(1.5));
                        if (level().noCollision(this, getBoundingBox().move(np.x - getX(), target.getY() - getY(), np.z - getZ()))) { dust(getX(), getY(), getZ(), 12); teleportTo(np.x, target.getY(), np.z); dust(getX(), getY(), getZ(), 12); }
                    }
                }
                if (t == a.hit && strike(target, 1.8f, r + 1.5, 0.6) && target != null) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                if (t == a.hit + 3) removeEffect(MobEffects.INVISIBILITY);
            }
            case IMPREGNATE -> tickImpregnate(a, target);
            case DEATH_ROLL -> tickDeathRoll(a, target);
            case LEAP_SLASH -> {
                if (t == 8) { setDeltaMovement(vx * 0.95, 0.5, vz * 0.95); hasImpulse = true; playSound(SoundEvents.RAVAGER_ROAR, 1f, 1.7f); }
                if (t >= 9 && t <= 22) for (LivingEntity e : around(1.6)) if (!sweptAlready(e) && strike(e, 1.1f, 3.0, 0.5)) markSwept(e);
            }
            case DROP_STRIKE -> {
                if (t == 1) {
                    boolean hung = isHanging(); if (hung) unhang();
                    Vec3 d = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : Vec3.ZERO; double l = d.length();
                    Vec3 dir = l > 1e-3 ? d.scale(1 / l) : fwd();
                    setDeltaMovement(dir.x * Math.min(0.55, l * 0.13), hung ? -0.5 : 0.55, dir.z * Math.min(0.55, l * 0.13)); hasImpulse = true;
                    playSound(SoundEvents.RAVAGER_ROAR, 1.5f, 1.8f);
                }
                if (t > 3 && onGround() && !landed) {
                    landed = true; dust(getX(), getY(), getZ(), 14); playSound(SoundEvents.GENERIC_EXPLODE, 1.2f, 0.9f);
                    for (LivingEntity e : around(3.6)) if (strike(e, 1.8f, 6, 0.9)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    attackTick = Math.max(attackTick, a.duration - 12);
                }
            }
            case FLAIL -> {
                if (t >= a.hit && t <= a.hit + 24 && (t - a.hit) % 3 == 0) {
                    for (LivingEntity e : around(r)) strike(e, 0.55f, r, 0.3);
                    setDeltaMovement(getDeltaMovement().add(fwd().scale(0.08).add(-fwd().z * (random.nextFloat() - 0.5) * 0.3, 0, fwd().x * (random.nextFloat() - 0.5) * 0.3)));
                }
            }
            case SPORE -> {
                if (t == a.hit && level() instanceof ServerLevel sl) {
                    var c = new net.minecraft.world.entity.AreaEffectCloud(level(), getX(), getY(), getZ());
                    c.setOwner(this); c.setRadius(4.5f); c.setDuration(140); c.setRadiusPerTick(-0.012f); c.setParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
                    c.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1)); c.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0)); c.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
                    sl.addFreshEntity(c);
                    sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + getBbHeight() * 0.6, getZ(), 60, 1.5, 0.6, 1.5, 0.05);
                    playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, 2f, 0.5f);
                }
            }
            case LOB -> { if (t == a.hit && target != null) { spitAt(target, 1.15f, 1f, true); playSound(SoundEvents.LLAMA_SPIT, 1.5f, 0.4f); } }
            case VOLLEY -> { if ((t == a.hit || t == a.hit + 5 || t == a.hit + 10) && target != null) { spitAt(target, 1.6f, 6f, false); playSound(SoundEvents.LLAMA_SPIT, 1.2f, 0.7f); } }
            case VOMIT -> {
                if (t >= a.hit && t <= a.hit + 24 && level() instanceof ServerLevel sl) {
                    Vec3 f = fwd(); double sc = Math.max(1, caste.scale * 0.9);
                    for (int i = 1; i <= 5; i++) sl.sendParticles(ParticleTypes.ITEM_SLIME, getX() + f.x * i * 1.3 * sc, getY() + getEyeHeight() - i * 0.12, getZ() + f.z * i * 1.3 * sc, 2, 0.25 * i * 0.4, 0.15, 0.25 * i * 0.4, 0.02);
                    if (t % 3 == 0) {
                        for (LivingEntity e : around(6.5 * sc)) {
                            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
                            if (d.lengthSqr() > 1e-4 && d.normalize().dot(f) > 0.78) { e.hurt(damageSources().magic(), caste.dmg * 0.35f); e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0)); }
                        }
                        playSound(SoundEvents.LAVA_EXTINGUISH, 1f, 0.8f);
                    }
                }
            }
            case DETONATE -> {
                if (t > a.hit - 14 && t < a.hit && t % 4 == 0) playSound(SoundEvents.CREEPER_PRIMED, 1f, 1.5f);
                if (t == a.hit) {
                    playSound(SoundEvents.GENERIC_EXPLODE, 2f, 0.9f);
                    if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.6, getZ(), 1, 0, 0, 0, 0);
                    for (LivingEntity e : around(5.5)) { strike(e, 1.5f, 8, 1.2); }
                    kill();
                }
            }
            case SUMMON -> {
                if (t == a.hit) {
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 1.3f);
                    if (level() instanceof ServerLevel sl && level().getEntitiesOfClass(XenoEntity.class, getBoundingBox().inflate(24)).size() < 16) {
                        for (Caste cc : brood()) {
                            XenoEntity m = XenomorphMod.XENOS.get(cc).get().create(sl);
                            double an = random.nextDouble() * Math.PI * 2, rr = 2.5 + random.nextDouble() * 2;
                            if (m == null) continue;
                            m.moveTo(getX() + Math.cos(an) * rr, getY(), getZ() + Math.sin(an) * rr, random.nextFloat() * 360, 0);
                            if (!sl.noCollision(m)) continue;
                            if (target != null) m.setTarget(target);
                            sl.addFreshEntity(m); sl.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                        }
                    }
                }
            }
            case FACE_TOSS -> {
                if ((t == 14 || t == 22) && target != null && level() instanceof ServerLevel sl && level().getEntitiesOfClass(FacehuggerEntity.class, getBoundingBox().inflate(16)).size() < 8) {
                    FacehuggerEntity f = XenomorphMod.FACEHUGGER.get().create(sl);
                    if (f != null) {
                        Vec3 p = position().add(fwd().scale(0.6 * caste.scale)).add(0, getBbHeight() * 0.55, 0);
                        f.moveTo(p.x, p.y, p.z, getYRot(), 0);
                        Vec3 d = target.position().add(0, target.getBbHeight() * 0.7, 0).subtract(p).normalize();
                        f.setDeltaMovement(d.x * 0.9, d.y * 0.9 + 0.3, d.z * 0.9); f.leapNow(); f.setTarget(target); sl.addFreshEntity(f);
                        playSound(SoundEvents.SLIME_SQUISH, 1.2f, 0.8f);
                    }
                }
            }
            case WEB -> {
                if (t == a.hit && target != null && !friendly(target)) {
                    if (level() instanceof ServerLevel sl) for (int i = 1; i <= 10; i++) { Vec3 p = position().add(0, getBbHeight() * 0.6, 0).lerp(target.position().add(0, target.getBbHeight() * 0.5, 0), i / 10.0); sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(net.minecraft.world.item.Items.COBWEB)), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0); }
                    Vec3 pull = position().subtract(target.position()).multiply(1, 0, 1).normalize();
                    target.setDeltaMovement(pull.x * 1.0, 0.25, pull.z * 1.0); target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                    playSound(SoundEvents.SPIDER_AMBIENT, 1.5f, 0.5f);
                }
            }
            case MULTI_STAB -> {
                if (t >= a.hit && t <= a.hit + 15 && (t - a.hit) % 3 == 0)
                    for (LivingEntity e : around(r + 1.0)) if (inFront(e, fwd())) strike(e, 0.6f, r + 1.0, 0.1);
            }
            case CREST_RAM -> {
                if (t > 5 && t < a.hit) setDeltaMovement(fwd().x * 0.45, getDeltaMovement().y, fwd().z * 0.45);
                if (t == a.hit) for (LivingEntity e : around(r + 0.5)) if (inFront(e, fwd()) && strike(e, 1.5f, r + 0.5, 2.0)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2)); e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                    playSound(SoundEvents.ANVIL_LAND, 1f, 0.6f);
                }
            }
            default -> {}
        }
    }
    private Caste[] brood() {
        return switch (caste) {
            case QUEEN -> new Caste[]{Caste.DRONE, Caste.DRONE, Caste.WARRIOR};
            case MATRIARCH -> new Caste[]{Caste.WARRIOR, Caste.WARRIOR, Caste.PRAETORIAN};
            case EMPRESS -> new Caste[]{Caste.PRAETORIAN, Caste.PRAETORIAN, Caste.RAVAGER};
            case QUEEN_MOTHER -> new Caste[]{Caste.WARRIOR, Caste.WARRIOR, Caste.CRUSHER, Caste.BRUTE, Caste.RAVAGER};
            default -> new Caste[]{Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_BLIGHT};
        };
    }
    private void launch(LivingEntity e, double up, double fw) { Vec3 f = fwd(); e.setDeltaMovement(f.x * fw, up, f.z * fw); e.hurtMarked = true; }
    private void dust(double x, double y, double z, int n) { if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, n, 0.5, 0.3, 0.5, 0.05); }
    private void spitAt(LivingEntity t, float speed, float inacc, boolean splash) {
        AcidSpit s = new AcidSpit(level(), this, caste.dmg * 0.8f); s.splash = splash;
        Vec3 e = position().add(0, getEyeHeight(), 0).add(fwd().scale(0.8 * caste.scale)); s.setPos(e);
        double dx = t.getX() - e.x, dy = t.getEyeY() - e.y - 0.1, dz = t.getZ() - e.z, h = Math.sqrt(dx * dx + dz * dz);
        s.shoot(dx, dy + h * (splash ? 0.22 : 0.05), dz, speed, inacc); level().addFreshEntity(s);
    }
    private boolean seize(LivingEntity target, Attack a, double extra) {
        if (attackTick == a.hit && held == null && target != null && !friendly(target) && distanceTo(target) <= reach() + extra) {
            held = target; held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 5)); playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.5f);
        }
        if (held == null) { if (attackTick > a.hit + 2) attackTick = a.duration; return false; }
        if (!held.isAlive()) { held = null; attackTick = a.duration; return false; }
        return true;
    }
    /** Pin the victim, then the tail/ovipositor implants an embryo. */
    private void tickImpregnate(Attack a, LivingEntity target) {
        int t = attackTick;
        if (!seize(target, a, 1.0)) return;
        double lift = Mth.clamp((t - a.hit) / 8.0, 0, 1);
        hold(position().add(fwd().scale(0.8 * caste.scale + 0.3)).add(0, getBbHeight() * 0.5 * lift, 0));
        if (t == 34) {
            if (!held.hasEffect(XenomorphMod.EMBRYO.get())) held.addEffect(new MobEffectInstance(XenomorphMod.EMBRYO.get(), 2400, 0));
            if (!hurtHeld(caste.dmg * 1.2f)) return;
            playSound(SoundEvents.SLIME_SQUISH, 1.5f, 0.5f);
        }
        if (t == a.duration - 6) releaseHeld(true);
    }
    /** Clamp on and spin like a crocodile. */
    private void tickDeathRoll(Attack a, LivingEntity target) {
        int t = attackTick;
        if (!seize(target, a, 1.0)) return;
        double ang = (t - a.hit) * 0.55; Vec3 f = fwd(), rt = new Vec3(-f.z, 0, f.x);
        hold(position().add(f.scale(0.9 * caste.scale + 0.2)).add(0, getBbHeight() * 0.45 + (Math.cos(ang) - 1) * 0.3, 0).add(rt.scale(Math.sin(ang) * 0.7 * caste.scale)));
        if (t >= 16 && (t - 16) % 8 == 0) { if (!hurtHeld(caste.dmg * 0.6f)) return; playSound(SoundEvents.RAVAGER_ATTACK, 1f, 0.9f); }
        if (t == a.duration - 6) releaseHeld(true);
    }
    /** Hoist overhead, slam into the ground twice, then hurl. */
    private void tickGrabSlam(Attack a, LivingEntity target) {
        int t = attackTick;
        if (!seize(target, a, 1.0)) return;
        double up = t < 30 ? Mth.clamp((t - a.hit) / 8.0, 0, 1) : t < 32 ? 0 : t < 44 ? Mth.clamp((t - 32) / 8.0, 0, 1) : 0;
        Vec3 f = fwd();
        hold(position().add(f.scale((up > 0 ? 0.3 : 1.4) * caste.scale)).add(0, getBbHeight() * (0.4 + 0.8 * up) * (up > 0 ? 1 : 0), 0));
        if (t == 30 || t == 44) {
            if (!hurtHeld(caste.dmg * 1.5f)) return;
            playSound(SoundEvents.GENERIC_EXPLODE, 1.5f, 0.7f); dust(held.getX(), held.getY(), held.getZ(), 14);
        }
        if (t == 48) releaseHeld(true);
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
            if (!hurtHeld(caste.dmg * 1.6f)) return;
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, held.getX(), held.getEyeY(), held.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
        if (t == a.duration - 6) releaseHeld(true);
    }
    // Tail pierces the victim, hoists it overhead, then flings it
    private void tickImpale(Attack a, LivingEntity target) {
        int t = attackTick;
        if (t == a.hit && held == null && target != null && !friendly(target) && distanceTo(target) <= 7) {
            held = target; impaled = true;
            if (!hurtHeld(caste.dmg * 1.8f)) return;
            playSound(SoundEvents.TRIDENT_HIT, 1.5f, 0.5f);
        }
        if (held == null) { if (t > a.hit + 2) attackTick = a.duration; return; }
        if (!held.isAlive()) { held = null; return; }
        double lift = Mth.clamp((t - a.hit) / 8.0, 0, 1);
        Vec3 f = fwd();
        hold(position().add(f.scale(0.6)).add(0, getBbHeight() * (0.9 + 0.35 * lift), 0));
        if (t == a.hit + 10 && !hurtHeld(caste.dmg * 0.8f)) return;
        if (t == a.duration - 4) releaseHeld(true);
    }
    /** Damages the held victim. Returns false if `held` was cleared meanwhile (e.g. thorns/reflect killed us and die() released it). */
    private boolean hurtHeld(float amount) {
        LivingEntity h = held;
        if (h == null) return false;
        h.hurt(damageSources().mobAttack(this), amount);
        return held != null && isAlive();
    }
    private void hold(Vec3 p) {
        if (held == null) return;
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
    @Override protected void playStepSound(net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) { { float w = caste.weight(); if (w >= 1.5f) { playSound(SoundEvents.IRON_GOLEM_STEP, 0.6f * w, 0.5f); if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 3, 0.5, 0.05, 0.5, 0.02); } else playSound(SoundEvents.SPIDER_STEP, 0.25f * w, 0.6f); } }
    @Override public boolean canBeAffected(MobEffectInstance i) {
        var e = i.getEffect();
        return e != MobEffects.POISON && e != MobEffects.WITHER && e != MobEffects.CONFUSION && e != MobEffects.HUNGER && super.canBeAffected(i);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Biped", isBiped()); tag.putBoolean("Hang", isHanging()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Biped") && caste.stance == 1) entityData.set(BIPED, tag.getBoolean("Biped")); if (tag.getBoolean("Hang")) { entityData.set(HANG, true); entityData.set(BIPED, false); setNoGravity(true); } }
}
