package com.xenomorph;

import com.xenomorph.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

/**
 * Natural hives: ~1 in 90 freshly generated overworld chunks gets a resin-lined cavern with eggs and a xeno colony
 * (a Pathogen hive 1 in 6 times). Built a few ticks after chunk load, inside that chunk's 16x16 so nothing crosses a border.
 */
@Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class Hive {
    private record Job(ServerLevel level, int cx, int cz, long due) {}
    private static final Queue<Job> JOBS = new ArrayDeque<>();
    private static final Caste[] QUEENS = {Caste.QUEEN, Caste.MATRIARCH, Caste.EMPRESS, Caste.QUEEN_MOTHER};
    private static final Caste[] NORMAL = {Caste.DRONE, Caste.DRONE, Caste.WARRIOR, Caste.WARRIOR, Caste.RUNNER, Caste.SPITTER, Caste.LURKER, Caste.PROWLER,
            Caste.SENTINEL, Caste.RAVAGER, Caste.CARRIER, Caste.ARACHNID, Caste.PRAETORIAN, Caste.CRUSHER, Caste.BRUTE, Caste.BULL_ALIEN, Caste.CHARGER, Caste.BOILER, Caste.BURSTER, Caste.STALKER};
    /** Spawn on their own in dark places (see biome modifiers), not in hives. */
    public static final Caste[] WILD = {Caste.CLONE_XENOMORPH, Caste.NEWBORN, Caste.WHITE_HYBRID, Caste.WHITE_KING, Caste.K_WARRIOR, Caste.K_FATALIS};
    private static final Caste[] RED = {Caste.RED_WARRIOR, Caste.RED_WARRIOR, Caste.RED_DRONE, Caste.RED_DRONE, Caste.RED_DRONE};
    private static final Caste[] PROTO = {Caste.PROTOMORPH};   // own faction: never shares a hive with classic xenos
    private static final Caste[] PATHOGEN = {Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_BLIGHT, Caste.PATHOGEN_BLIGHT, Caste.PATHOGEN_BRUTE};
    private static final Caste[] NEO = {Caste.NEOMORPH};   // own faction
    /** Every caste a natural hive can produce, directly or via a cocooned host (Predator -> Predalien, Engineer -> Deacon). */
    public static java.util.Set<Caste> reachable() {
        java.util.EnumSet<Caste> r = java.util.EnumSet.noneOf(Caste.class);
        for (Caste[] a : new Caste[][]{QUEENS, NORMAL, PATHOGEN, PROTO, NEO, RED, WILD, {Caste.TRUE_KING}, {Caste.RED_QUEEN_MOTHER}, {Caste.KING, Caste.PATHOGEN_QUEEN, Caste.PREDALIEN, Caste.BERSERKER, Caste.DEACON}}) r.addAll(java.util.Arrays.asList(a));
        return r;
    }

    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || !e.isNewChunk()) return;
        int cx = e.getChunk().getPos().x, cz = e.getChunk().getPos().z;
        if (new Random(sl.getSeed() ^ (cx * 341873128712L + cz * 132897987541L)).nextInt(90) == 0) JOBS.add(new Job(sl, cx, cz, sl.getGameTime() + 60));
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        Job j = JOBS.peek();
        if (j == null || j.level != sl || j.due > sl.getGameTime()) return;
        JOBS.poll();
        try { build(sl, j.cx, j.cz, null); } catch (RuntimeException ex) { XenomorphMod.LOG.warn("hive build failed", ex); }
    }

    public static void build(ServerLevel sl, int cx, int cz, Integer forceY) { build(sl, cx, cz, forceY, false); }

    public static void build(ServerLevel sl, int cx, int cz, Integer forceY, boolean forceRed) {
        Random r = new Random(sl.getSeed() ^ (cx * 7919L + cz * 104729L));
        int x0 = cx * 16 + 8, z0 = cz * 16 + 8;
        if (!sl.hasChunk(cx, cz)) return;
        int surf = sl.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x0, z0);
        int y0 = forceY != null ? forceY : Math.max(sl.getMinBuildHeight() + 12, Math.min(surf - 24, 30));
        if (forceY == null && surf - y0 < 16) return;                                    // too shallow (ocean / flat low terrain)
        BlockState resin = XenomorphMod.RESIN.get().defaultBlockState();
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) for (int dy = -7; dy <= 7; dy++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.7 + dz * dz) + r.nextDouble() * 0.6;
            BlockPos p = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
            BlockState st = sl.getBlockState(p);
            if (st.is(Blocks.BEDROCK)) continue;
            if (d <= 5.6) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            else if (d <= 7.0 && !st.isAir()) sl.setBlock(p, resin, 2);
            else if (d <= 7.0 && dy < -4) sl.setBlock(p, resin, 2);   // seal the floor over open caves
        }
        // floor: resin patches inside
        for (int i = 0; i < 40; i++) { BlockPos f = floor(sl, x0 + r.nextInt(11) - 5, y0, z0 + r.nextInt(11) - 5); if (f != null) sl.setBlock(f.below(), resin, 2); }
        boolean pathogen = !forceRed && r.nextInt(6) == 0, proto = !forceRed && !pathogen && r.nextInt(7) == 0, neo = !forceRed && !pathogen && !proto && r.nextInt(7) == 0, red = forceRed || (!pathogen && !proto && !neo && r.nextInt(8) == 0);
        Caste q = red ? Caste.RED_QUEEN_MOTHER : neo ? Caste.NEOMORPH : proto ? Caste.PROTOMORPH : pathogen ? Caste.PATHOGEN_QUEEN : QUEENS[r.nextInt(100) < 70 ? 0 : 1 + r.nextInt(3)];
        spawn(sl, q, x0 + 0.5, y0 - 3, z0 + 0.5, r);
        if (!proto && !neo && !red && r.nextInt(8) == 0) spawn(sl, Caste.KING, x0 + 2.5, y0 - 3, z0 - 2.5, r);
        if (!proto && !neo && !red && !pathogen && r.nextInt(20) == 0) spawn(sl, Caste.TRUE_KING, x0 - 2.5, y0 - 3, z0 + 2.5, r);   // the apex caste, very rare
        int n = 5 + r.nextInt(5);
        for (int i = 0; i < n; i++) {
            Caste[] pool = red ? RED : neo ? NEO : proto ? PROTO : pathogen ? PATHOGEN : NORMAL;
            BlockPos f = floor(sl, x0 + r.nextInt(11) - 5, y0, z0 + r.nextInt(11) - 5);
            if (f != null) spawn(sl, pool[r.nextInt(pool.length)], f.getX() + .5, f.getY(), f.getZ() + .5, r);
        }
        // cocooned hosts: embryo already inside, born (after the timer) as Predalien / Deacon
        cocoon(sl, HunterEntity.Kind.PREDATOR, r.nextInt(3) == 0, red, x0, y0, z0, r);
        cocoon(sl, HunterEntity.Kind.ENGINEER, r.nextInt(6) == 0, red, x0, y0, z0, r);
        for (int i = 0; i < 7; i++) {
            BlockPos f = floor(sl, x0 + r.nextInt(11) - 5, y0, z0 + r.nextInt(11) - 5);
            XenoEggEntity egg = XenomorphMod.EGG.get().create(sl);
            if (f != null && egg != null) { egg.setRed(red); egg.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, r.nextFloat() * 360, 0); sl.addFreshEntity(egg); }
        }
    }

    private static void cocoon(ServerLevel sl, HunterEntity.Kind k, boolean yes, boolean red, int x0, int y0, int z0, Random r) {
        if (!yes) return;
        BlockPos f = floor(sl, x0 + r.nextInt(9) - 4, y0, z0 + r.nextInt(9) - 4);
        HunterEntity h = XenomorphMod.HUNTERS.get(k).get().create(sl);
        if (f == null || h == null) return;
        h.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, r.nextFloat() * 360, 0);
        h.setNoAi(true); h.setPersistenceRequired();
        h.addEffect(new net.minecraft.world.effect.MobEffectInstance((red ? XenomorphMod.RED_EMBRYO : XenomorphMod.EMBRYO).get(), 1800 + r.nextInt(1200), 0, false, false));
        sl.addFreshEntity(h);
    }

    private static BlockPos floor(ServerLevel sl, int x, int yc, int z) {
        for (int y = yc + 4; y > yc - 7; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (sl.getBlockState(p).isAir() && sl.getBlockState(p.above()).isAir() && sl.getBlockState(p.below()).isSolid()) return p;
        }
        return null;
    }

    private static void spawn(ServerLevel sl, Caste c, double x, double y, double z, Random r) {
        XenoEntity m = XenomorphMod.XENOS.get(c).get().create(sl);
        if (m == null) return;
        BlockPos f = floor(sl, (int) Math.floor(x), (int) y + 3, (int) Math.floor(z));
        m.moveTo(x, f != null ? f.getY() : y, z, r.nextFloat() * 360, 0);
        m.setPersistenceRequired();
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        sl.addFreshEntity(m);
    }
}
