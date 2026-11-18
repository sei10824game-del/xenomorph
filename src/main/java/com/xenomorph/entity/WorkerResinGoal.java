package com.xenomorph.entity;

import com.xenomorph.XenoConfig;
import com.xenomorph.XenomorphMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Workers build the hive: pick a bare wall/floor block near the resin, walk to it, secrete for 3 s, and coat it. */
public class WorkerResinGoal extends Goal {
    private final XenoEntity x;
    private BlockPos spot, stand;
    private int work, cool = 200;

    public WorkerResinGoal(XenoEntity x) { this.x = x; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    @Override public boolean canUse() {
        if (!XenoConfig.HIVE_CREEP.get() || x.getTarget() != null || x.isHanging() || --cool > 0 || !(x.level() instanceof ServerLevel sl)) return false;
        cool = 100 + x.getRandom().nextInt(100);
        var resin = XenomorphMod.RESIN.get();
        int have = 0;
        BlockPos c = x.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 5, 8))) if (sl.getBlockState(q).is(resin)) have++;
        if (have >= 90) return false;
        for (int i = 0; i < 30; i++) {
            BlockPos q = c.offset(x.getRandom().nextInt(13) - 6, x.getRandom().nextInt(7) - 2, x.getRandom().nextInt(13) - 6);
            var st = sl.getBlockState(q);
            if (!(st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.DIRT))) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos a = q.relative(d);
                if (!sl.getBlockState(a).isAir() || !sl.getBlockState(a.above()).isAir() || !sl.getBlockState(a.below()).isSolid()) continue;
                boolean near = have == 0;
                for (Direction e : Direction.values()) if (sl.getBlockState(q.relative(e)).is(resin)) near = true;
                if (near) { spot = q; stand = a; return true; }
            }
        }
        return false;
    }
    @Override public boolean canContinueToUse() { return spot != null && x.getTarget() == null && work < 60; }
    @Override public void start() { work = 0; x.getNavigation().moveTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5, 1.0); }
    @Override public void stop() { spot = null; x.getNavigation().stop(); }
    @Override public void tick() {
        x.getLookControl().setLookAt(spot.getX() + .5, spot.getY() + .5, spot.getZ() + .5);
        if (x.distanceToSqr(stand.getX() + .5, stand.getY(), stand.getZ() + .5) > 2.5) { if (x.getNavigation().isDone()) x.getNavigation().moveTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5, 1.0); if (++work > 400) work = 60; return; }
        x.getNavigation().stop();
        if (!(x.level() instanceof ServerLevel sl)) return;
        if (work++ % 10 == 0) { sl.sendParticles(ParticleTypes.ITEM_SLIME, spot.getX() + .5, spot.getY() + .5, spot.getZ() + .5, 3, 0.3, 0.3, 0.3, 0.01); x.playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.6f, 0.6f); }
        if (work >= 60) { if (!sl.getBlockState(spot).isAir()) sl.setBlock(spot, XenomorphMod.RESIN.get().defaultBlockState(), 3); }
    }
}
