package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Ovomorph: opens when prey approaches and releases a facehugger. */
public class XenoEggEntity extends Entity {
    private static final EntityDataAccessor<Integer> OPEN = SynchedEntityData.defineId(XenoEggEntity.class, EntityDataSerializers.INT); // 0 closed .. 40 fully open
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(XenoEggEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean hugger = true;
    private int idle;
    public int openO;

    public XenoEggEntity(EntityType<?> t, Level l) { super(t, l); }
    @Override protected void defineSynchedData() { entityData.define(OPEN, 0); entityData.define(RED, false); }
    public boolean red() { return entityData.get(RED); }
    public void setRed(boolean r) { entityData.set(RED, r); }
    private void spawnHugger(ServerLevel sl, double y) {
        FacehuggerEntity f = XenomorphMod.FACEHUGGER.get().create(sl);
        if (f == null) return;
        if (red()) f.setVariant(random.nextInt(4) == 0 ? 2 : 1);
        f.moveTo(getX(), getY() + y, getZ(), random.nextFloat() * 360, 0); f.setDeltaMovement(0, 0.35, 0); sl.addFreshEntity(f);
    }
    public int open() { return entityData.get(OPEN); }

    @Override public void tick() {
        super.tick();
        openO = open();
        if (!isNoGravity()) { setDeltaMovement(getDeltaMovement().add(0, -0.04, 0)); move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement()); setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6)); }
        if (level().isClientSide) return;
        boolean prey = !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5, 2, 4.5), e -> !XenoEntity.isXeno(e) && e.isAlive() && !e.isSpectator()
                && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative())).isEmpty();
        int o = open();
        if (prey) { idle = 0; if (o < 40) entityData.set(OPEN, o + 2); }
        else if (++idle > 100 && o > 0 && hugger) entityData.set(OPEN, o - 1);
        if (o >= 40 && hugger && prey && level() instanceof ServerLevel sl) {
            spawnHugger(sl, 1.0);
            hugger = false; playSound(SoundEvents.SLIME_SQUISH, 1f, 0.5f);
        }
    }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource s, float a) {
        if (level().isClientSide || isRemoved()) return false;
        playSound(SoundEvents.SLIME_SQUISH, 1f, 0.7f);
        if (hugger && level() instanceof ServerLevel sl) {
            spawnHugger(sl, 0.8);
        }
        discard();
        return true;
    }
    @Override protected void readAdditionalSaveData(CompoundTag t) { hugger = !t.contains("Hugger") || t.getBoolean("Hugger"); setRed(t.getBoolean("Red")); }
    @Override protected void addAdditionalSaveData(CompoundTag t) { t.putBoolean("Hugger", hugger); t.putBoolean("Red", red()); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
