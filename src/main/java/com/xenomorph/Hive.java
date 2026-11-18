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
    private static final Caste[] QUEENS = {Caste.QUEEN, Caste.MATRIARCH, Caste.EMPRESS, Caste.QUEEN_MOTHER, Caste.PREDALIEN_QUEEN, Caste.DOG_QUEEN, Caste.FLYING_QUEEN, Caste.VAMPIRIC_QUEEN};
    private static final Caste[] NORMAL = {Caste.DRONE, Caste.DRONE, Caste.WARRIOR, Caste.WARRIOR, Caste.RUNNER, Caste.SPITTER, Caste.LURKER, Caste.PROWLER,
            Caste.SENTINEL, Caste.RAVAGER, Caste.CARRIER, Caste.ARACHNID, Caste.PRAETORIAN, Caste.CRUSHER, Caste.BRUTE, Caste.BULL_ALIEN, Caste.CHARGER, Caste.BOILER, Caste.BURSTER, Caste.STALKER, Caste.WORKER, Caste.PALATINE, Caste.SMASHER, Caste.SPIKER, Caste.WARDEN, Caste.EXPLODER, Caste.SNATCHER, Caste.REAPER, Caste.IRRADIATED, Caste.SCORCHED, Caste.WRAITH, Caste.ARMORED, Caste.TUSKED, Caste.IMP, Caste.TITAN, Caste.ALPHA, Caste.BIODRONE, Caste.INFECTOID, Caste.SYMBIOTIC, Caste.BELLYBURSTER, Caste.CRAWLER, Caste.BIOMORPH, Caste.SIREN, Caste.SEVEN};
    /** Spawn on their own in dark places (see biome modifiers), not in hives. */
    public static final Caste[] WILD = {Caste.CLONE_XENOMORPH, Caste.NEWBORN, Caste.WHITE_HYBRID, Caste.WHITE_KING, Caste.K_WARRIOR, Caste.K_FATALIS};
    /** Only ever born from animal hosts (see EmbryoEffect.hostForm); hives carry cocooned animals so they appear naturally. */
    private static final Caste[] HOSTED = {Caste.PANTHER, Caste.GOAT, Caste.BAT, Caste.WINTERBEAST};
    private static final net.minecraft.world.entity.EntityType<?>[] ANIMALS = {net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.OCELOT, net.minecraft.world.entity.EntityType.GOAT, net.minecraft.world.entity.EntityType.POLAR_BEAR, net.minecraft.world.entity.EntityType.BAT, net.minecraft.world.entity.EntityType.WOLF};
    private static final Caste[] RED = {Caste.RED_WARRIOR, Caste.RED_WARRIOR, Caste.RED_DRONE, Caste.RED_DRONE, Caste.RED_DRONE};
    private static final Caste[] KSER = {Caste.K_DRONE, Caste.K_DRONE, Caste.K_DRONE, Caste.K_WARRIOR, Caste.K_WARRIOR, Caste.K_PRAETORIAN, Caste.K_FATALIS};
    private static final Caste[] CLONES = {Caste.CLONE_XENOMORPH, Caste.CLONE_XENOMORPH, Caste.CLONE_XENOMORPH, Caste.CLONE_WARRIOR, Caste.CLONE_WARRIOR, Caste.CLONE_PRAETORIAN};
    private static final Caste[] PROTO = {Caste.PROTOMORPH};   // own faction: never shares a hive with classic xenos
    private static final Caste[] PATHOGEN = {Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_RUNNER, Caste.PATHOGEN_BLIGHT, Caste.PATHOGEN_BLIGHT, Caste.PATHOGEN_BRUTE, Caste.HARBINGER, Caste.THRASHER, Caste.GOREBURSTER};
    private static final Caste[] NEO = {Caste.NEOMORPH, Caste.NEOMORPH, Caste.BLOODBURSTER};   // own faction
    /** Every caste a natural hive can produce, directly or via a cocooned host (Predator -> Predalien, Engineer -> Deacon). */
    public static java.util.Set<Caste> reachable() {
        java.util.EnumSet<Caste> r = java.util.EnumSet.noneOf(Caste.class);
        for (Caste[] a : new Caste[][]{QUEENS, NORMAL, HOSTED, PATHOGEN, PROTO, NEO, RED, KSER, CLONES, {Caste.K_QUEEN, Caste.CLONE_QUEEN}, WILD, {Caste.TRUE_KING}, {Caste.RED_QUEEN_MOTHER}, {Caste.KING, Caste.PATHOGEN_QUEEN, Caste.PREDALIEN, Caste.BERSERKER, Caste.DEACON, Caste.ULTRAMORPH}}) r.addAll(java.util.Arrays.asList(a));
        return r;
    }

    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || !e.isNewChunk()) return;
        int cx = e.getChunk().getPos().x, cz = e.getChunk().getPos().z;
        if (new Random(sl.getSeed() ^ (cx * 341873128712L + cz * 132897987541L)).nextInt(XenoConfig.HIVE_RARITY.get()) == 0) JOBS.add(new Job(sl, cx, cz, sl.getGameTime() + 60));
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 6000 == 3000) regrow(sl);
        if (sl.getGameTime() % 20 == 0) warnTerritory(sl);
        Job j = JOBS.peek();
        if (j == null || j.level != sl || j.due > sl.getGameTime()) return;
        JOBS.poll();
        try { build(sl, j.cx, j.cz, null); } catch (RuntimeException ex) { XenomorphMod.LOG.warn("hive build failed", ex); }
    }

    private static final java.util.Set<java.util.UUID> INSIDE = new java.util.HashSet<>();
    /** Walking into a hive's territory (within 14 blocks of its core) gives a one-time warning with a heartbeat. */
    private static void warnTerritory(ServerLevel sl) {
        var data = HiveData.get(sl);
        for (var p : sl.players()) {
            boolean in = false;
            for (HiveData.Entry h : data.hives) if (h.pos().distSqr(p.blockPosition()) < 196) { in = true; break; }
            if (in && INSIDE.add(p.getUUID())) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenomorph.territory").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
                p.playNotifySound(net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.AMBIENT, 1f, 0.8f);
            } else if (!in) INSIDE.remove(p.getUUID());
        }
    }

    /** Resin creeps over natural stone/dirt/wood touching open air near the hive core, a few blocks per pass, capped at ~40 resin in range. */
    private static void creep(ServerLevel sl, BlockPos c, Random r) {
        var resin = XenomorphMod.RESIN.get();
        int have = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-9, -4, -9), c.offset(9, 6, 9))) if (sl.getBlockState(q).is(resin)) have++;
        if (have >= 40) return;
        for (int i = 0, done = 0; i < 40 && done < 3; i++) {
            BlockPos q = c.offset(r.nextInt(19) - 9, r.nextInt(11) - 4, r.nextInt(19) - 9);
            var st = sl.getBlockState(q);
            if (!(st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(net.minecraft.tags.BlockTags.LOGS))) continue;
            boolean exposed = false;
            for (var d : net.minecraft.core.Direction.values()) if (sl.getBlockState(q.relative(d)).isAir()) { exposed = true; break; }
            boolean near = false;
            for (var d : net.minecraft.core.Direction.values()) if (sl.getBlockState(q.relative(d)).is(resin)) { near = true; break; }
            if (exposed && (near || have == 0)) { sl.setBlock(q, resin.defaultBlockState(), 3); done++; }
        }
    }

    /** Hives slowly regrow: every 5 min, a hive with a player nearby that has fewer than 6 eggs around lays a fresh one. */
    private static void regrow(ServerLevel sl) {
        Random r = new Random(sl.getGameTime());
        for (HiveData.Entry h : HiveData.get(sl).hives) {
            if (sl.getNearestPlayer(h.pos().getX(), h.pos().getY(), h.pos().getZ(), 80, false) == null || !sl.isLoaded(h.pos())) continue;
            var box = new net.minecraft.world.phys.AABB(h.pos()).inflate(16);
            if (XenoConfig.HIVE_CREEP.get() && !sl.getEntitiesOfClass(XenoEntity.class, box).isEmpty()) creep(sl, h.pos(), r);
            if (sl.getEntitiesOfClass(XenoEggEntity.class, box).size() >= 6 || sl.getEntitiesOfClass(XenoEntity.class, box).isEmpty()) continue;   // only while some xeno still guards it
            BlockPos f = floor(sl, h.pos().getX() + r.nextInt(11) - 5, h.pos().getY(), h.pos().getZ() + r.nextInt(11) - 5);
            XenoEggEntity egg = XenomorphMod.EGG.get().create(sl);
            if (f != null && egg != null) { egg.setRed(h.kind().equals("red")); egg.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, r.nextFloat() * 360, 0); sl.addFreshEntity(egg); }
        }
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
        for (int i = 0; i < 5; i++) {   // resin pillars floor-to-ceiling
            BlockPos f = floor(sl, x0 + r.nextInt(9) - 4, y0, z0 + r.nextInt(9) - 4);
            if (f != null) for (int y = 0; y < 9; y++) { BlockPos q2 = f.above(y); if (sl.getBlockState(q2).isAir()) sl.setBlock(q2, resin, 2); else break; }
        }
        for (int i = 0; i < 40; i++) { BlockPos f = floor(sl, x0 + r.nextInt(11) - 5, y0, z0 + r.nextInt(11) - 5); if (f != null) sl.setBlock(f.below(), resin, 2); }
        boolean pathogen = !forceRed && r.nextInt(6) == 0, proto = !forceRed && !pathogen && r.nextInt(7) == 0, neo = !forceRed && !pathogen && !proto && r.nextInt(7) == 0, red = forceRed || (!pathogen && !proto && !neo && r.nextInt(8) == 0),
                kser = !forceRed && !pathogen && !proto && !neo && !red && r.nextInt(9) == 0, clone = !forceRed && !pathogen && !proto && !neo && !red && !kser && r.nextInt(9) == 0;
        Caste q = kser ? Caste.K_QUEEN : clone ? Caste.CLONE_QUEEN : red ? Caste.RED_QUEEN_MOTHER : neo ? Caste.NEOMORPH : proto ? Caste.PROTOMORPH : pathogen ? Caste.PATHOGEN_QUEEN : QUEENS[r.nextInt(100) < 70 ? 0 : 1 + r.nextInt(QUEENS.length - 1)];
        spawn(sl, q, x0 + 0.5, y0 - 3, z0 + 0.5, r);
        HiveData.get(sl).add(new BlockPos(x0, y0, z0), q.id);
        if (!proto && !neo && !red && !kser && !clone && r.nextInt(8) == 0) spawn(sl, Caste.KING, x0 + 2.5, y0 - 3, z0 - 2.5, r);
        if (!proto && !neo && !red && !kser && !clone && !pathogen && r.nextInt(20) == 0) spawn(sl, Caste.TRUE_KING, x0 - 2.5, y0 - 3, z0 + 2.5, r);   // the apex caste, very rare
        int n = 5 + r.nextInt(5);
        for (int i = 0; i < n; i++) {
            Caste[] pool = kser ? KSER : clone ? CLONES : red ? RED : neo ? NEO : proto ? PROTO : pathogen ? PATHOGEN : NORMAL;
            BlockPos f = floor(sl, x0 + r.nextInt(11) - 5, y0, z0 + r.nextInt(11) - 5);
            if (f != null) spawn(sl, pool[r.nextInt(pool.length)], f.getX() + .5, f.getY(), f.getZ() + .5, r);
        }
        if (r.nextInt(3) == 0) {   // a cocooned animal: its brood inherits the species' traits
            BlockPos f = floor(sl, x0 + r.nextInt(9) - 4, y0, z0 + r.nextInt(9) - 4);
            net.minecraft.world.entity.Entity an = ANIMALS[r.nextInt(ANIMALS.length)].create(sl);
            if (f != null && an instanceof net.minecraft.world.entity.Mob m) {
                m.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, r.nextFloat() * 360, 0); m.setNoAi(true); m.setPersistenceRequired();
                m.addEffect(new net.minecraft.world.effect.MobEffectInstance((red ? XenomorphMod.RED_EMBRYO : XenomorphMod.EMBRYO).get(), 1800 + r.nextInt(1200), 0, false, false));
                sl.addFreshEntity(m);
            }
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
