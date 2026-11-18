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
    /** The mutant lineage (Mutant Monsters-style apex castes): rare guardians of classic hives. */
    private static final Caste[] MUTANTS = {Caste.HULK_XENO, Caste.BONE_XENO, Caste.BLAST_XENO, Caste.VOID_XENO, Caste.FROST_XENO};
    private static final Caste[] NEO = {Caste.NEOMORPH, Caste.NEOMORPH, Caste.BLOODBURSTER};   // own faction
    /** Every caste a natural hive can produce, directly or via a cocooned host (Predator -> Predalien, Engineer -> Deacon). */
    public static java.util.Set<Caste> reachable() {
        java.util.EnumSet<Caste> r = java.util.EnumSet.noneOf(Caste.class);
        for (Caste[] a : new Caste[][]{QUEENS, NORMAL, HOSTED, PATHOGEN, PROTO, NEO, RED, KSER, CLONES, {Caste.K_QUEEN, Caste.CLONE_QUEEN}, WILD, {Caste.TRUE_KING}, MUTANTS, {Caste.RED_QUEEN_MOTHER}, {Caste.KING, Caste.PATHOGEN_QUEEN, Caste.PREDALIEN, Caste.BERSERKER, Caste.DEACON, Caste.ULTRAMORPH}}) r.addAll(java.util.Arrays.asList(a));
        return r;
    }

    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || !e.isNewChunk()) return;
        int cx = e.getChunk().getPos().x, cz = e.getChunk().getPos().z;
        if (new Random(sl.getSeed() ^ (cx * 341873128712L + cz * 132897987541L)).nextInt(Math.max(1, (int) (XenoConfig.HIVE_RARITY.get() / XenoConfig.hiveFreq()))) == 0) JOBS.add(new Job(sl, cx, cz, sl.getGameTime() + 60));
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 6000 == 3000) regrow(sl);
        if (sl.getGameTime() % 20 == 0) warnTerritory(sl);
        if (sl.getGameTime() % 12000 == 6000 && XenoConfig.HIVE_RAIDS.get()) raid(sl);
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

    /** Every 10 minutes a guarded hive may send a raiding party at the nearest settlement (villager, golem or survival player) 30-160 blocks away. */
    private static void raid(ServerLevel sl) {
        Random r = new Random(sl.getGameTime());
        for (HiveData.Entry h : HiveData.get(sl).hives) raidOne(sl, h, r, false);
    }
    /** One hive's raid roll; force skips the 1-in-3 chance (used by tests). Returns how many raiders were sent. */
    static int raidOne(ServerLevel sl, HiveData.Entry h, Random r, boolean force) {
        {
            if (!sl.isLoaded(h.pos()) || (!force && r.nextInt(3) != 0)) return 0;
            var box = new net.minecraft.world.phys.AABB(h.pos()).inflate(16);
            if (sl.getEntitiesOfClass(XenoEntity.class, box).size() < 3) return 0;
            XenoEntity guard = sl.getEntitiesOfClass(XenoEntity.class, box).get(0);
            // any living thing outside the hive's own faction is fair game: villagers, golems, players, other mods' mobs, bosses and rival queens
            var prey = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(h.pos()).inflate(160), e -> e.isAlive() && h.pos().distSqr(e.blockPosition()) > 900
                    && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !guard.friendly(e)
                    && !(e instanceof net.minecraft.world.entity.player.Player pl && (pl.isCreative() || pl.isSpectator()))
                    && !(e instanceof net.minecraft.world.entity.animal.Animal && e.getMaxHealth() < 40));   // livestock is not worth a raid unless it is huge
            if (prey.isEmpty()) return 0;
            // priority: bosses and rival queens first (highest max health within range), otherwise the nearest settlement/creature
            var tgt = prey.stream().filter(e -> e.getMaxHealth() >= 150 || (e instanceof XenoEntity q && q.caste.attacks.contains(com.xenomorph.entity.Attack.LAY))).max(java.util.Comparator.comparingDouble(net.minecraft.world.entity.LivingEntity::getMaxHealth))
                    .orElseGet(() -> prey.stream().min(java.util.Comparator.comparingDouble(e -> h.pos().distSqr(e.blockPosition()))).get());
            boolean tough = tgt.getMaxHealth() >= 100;
            Caste[] pool = h.kind().startsWith("red") ? new Caste[]{Caste.RED_DRONE, Caste.RED_WARRIOR} : h.kind().startsWith("pathogen") ? new Caste[]{Caste.PATHOGEN_RUNNER} : new Caste[]{Caste.DRONE, Caste.WARRIOR, Caste.RUNNER};
            if (tough) pool = h.kind().startsWith("red") ? new Caste[]{Caste.RED_WARRIOR} : new Caste[]{Caste.WARRIOR, Caste.PRAETORIAN, Caste.BRUTE, Caste.CRUSHER, Caste.RAVAGER};
            int n = 3 + r.nextInt(3) + (int) Math.min(5, tgt.getMaxHealth() / 60), made = 0;
            for (int i = 0; i < n * 3 && made < n; i++) {
                double an = r.nextDouble() * Math.PI * 2, rad = 26 + r.nextInt(8);
                BlockPos f = floor(sl, (int) (tgt.getX() + Math.cos(an) * rad), tgt.getBlockY(), (int) (tgt.getZ() + Math.sin(an) * rad));
                if (f == null) continue;
                XenoEntity m = XenomorphMod.XENOS.get(pool[r.nextInt(pool.length)]).get().create(sl);
                if (m == null) continue;
                m.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, r.nextFloat() * 360, 0);
                m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(f), MobSpawnType.EVENT, null, null);
                sl.addFreshEntity(m); m.raid(tgt); made++;
            }
            if (made > 0) for (var pl : sl.players()) if (pl.distanceToSqr(tgt) < 4096) pl.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenomorph.raid").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
            return made;
        }
    }

    /** Digs a 3-wide, 4-high ramp from the hive up to open sky (resin floor), along whichever compass direction reaches the surface soonest. */
    static void tunnel(ServerLevel sl, BlockPos core) { tunnel(sl, core, (x, z) -> sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)); }
    static void tunnel(ServerLevel sl, BlockPos core, java.util.function.IntBinaryOperator surface) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int bestDir = 0, bestLen = 999;
        for (int d = 0; d < 4; d++) {
            for (int i = 4; i < 90; i++) {
                int x = core.getX() + dirs[d][0] * i, z = core.getZ() + dirs[d][1] * i;
                if (core.getY() - 4 + (i - 4) >= surface.applyAsInt(x, z)) { if (i < bestLen) { bestLen = i; bestDir = d; } break; }
            }
        }
        int dx = dirs[bestDir][0], dz = dirs[bestDir][1], px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0;
        var resin = XenomorphMod.RESIN.get().defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int i = 4; i < Math.min(bestLen + 4, 96); i++) {
            int cx = core.getX() + dx * i, cz = core.getZ() + dz * i, fy = core.getY() - 4 + (i - 4);   // the hive floor sits about 4 below its core
            for (int w = -1; w <= 1; w++) {
                int x = cx + px * w, z = cz + pz * w;
                BlockPos floor = new BlockPos(x, fy - 1, z);
                if (sl.getBlockState(floor).getDestroySpeed(sl, floor) >= 0) sl.setBlock(floor, resin, 2);
                for (int h = 0; h < 4; h++) {
                    BlockPos q = new BlockPos(x, fy + h, z);
                    var st = sl.getBlockState(q);
                    if (st.getDestroySpeed(sl, q) < 0) continue;                  // never touch bedrock
                    sl.setBlock(q, st.getFluidState().isEmpty() ? air : resin, 2);   // water/lava is sealed off with resin instead of flooding the tunnel
                }
            }
        }
        // the mouth: two ribbed resin pillars with a lintel mark the entrance so it can be spotted from the surface
        int ex = core.getX() + dx * bestLen, ez = core.getZ() + dz * bestLen, ey = core.getY() - 4 + (bestLen - 4);
        for (int side = -2; side <= 2; side += 4) for (int h = 0; h < 6; h++) { BlockPos q = new BlockPos(ex + px * side, ey + h, ez + pz * side); if (sl.getBlockState(q).getDestroySpeed(sl, q) >= 0) sl.setBlock(q, resin, 2); }
        for (int w = -2; w <= 2; w++) { BlockPos q = new BlockPos(ex + px * w, ey + 5, ez + pz * w); if (sl.getBlockState(q).getDestroySpeed(sl, q) >= 0) sl.setBlock(q, resin, 2); }
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
            if (!HiveData.get(sl).isTunneled(h.pos())) { try { tunnel(sl, h.pos()); } catch (RuntimeException ex) { XenomorphMod.LOG.warn("hive tunnel failed", ex); } HiveData.get(sl).markTunneled(h.pos()); }   // hives built before tunnels existed get theirs on the next pass
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
        try { tunnel(sl, new BlockPos(x0, y0, z0)); HiveData.get(sl).markTunneled(new BlockPos(x0, y0, z0)); } catch (RuntimeException ex) { XenomorphMod.LOG.warn("hive tunnel failed", ex); }
        if (!proto && !neo && !red && !kser && !clone && r.nextInt(8) == 0) spawn(sl, Caste.KING, x0 + 2.5, y0 - 3, z0 - 2.5, r);
        if (!proto && !neo && !red && !kser && !clone && !pathogen && r.nextInt(20) == 0) spawn(sl, Caste.TRUE_KING, x0 - 2.5, y0 - 3, z0 + 2.5, r);   // the apex caste, very rare
        if (!proto && !neo && !red && !kser && !clone && !pathogen && r.nextInt(6) == 0) { BlockPos mf = floor(sl, x0 + r.nextInt(9) - 4, y0, z0 + r.nextInt(9) - 4); if (mf != null) spawn(sl, MUTANTS[r.nextInt(MUTANTS.length)], mf.getX() + .5, mf.getY(), mf.getZ() + .5, r); }
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
        // hive dressing: a ring of eggs around the queen's chamber (classic/red) and resin stalactites hanging from the ceiling
        if (!pathogen && !proto && !neo) for (int i = 0; i < 6; i++) {
            double an = i * Math.PI / 3; BlockPos f = floor(sl, x0 + (int) Math.round(Math.cos(an) * 4), y0, z0 + (int) Math.round(Math.sin(an) * 4));
            XenoEggEntity egg = XenomorphMod.EGG.get().create(sl);
            if (f != null && egg != null) { egg.setRed(red); egg.moveTo(f.getX() + .5, f.getY(), f.getZ() + .5, (float) Math.toDegrees(an), 0); sl.addFreshEntity(egg); }
        }
        for (int i = 0, nq = red ? 22 : 14; i < nq; i++) {
            int sx = x0 + r.nextInt(15) - 7, sz = z0 + r.nextInt(15) - 7;
            for (int y = y0 + 1; y < y0 + 9; y++) {
                BlockPos c = new BlockPos(sx, y, sz);
                if (sl.getBlockState(c).isSolid() && sl.getBlockState(c.below()).isAir()) {
                    int len = 1 + r.nextInt(3);
                    for (int k = 1; k <= len && sl.getBlockState(c.below(k)).isAir(); k++) sl.setBlock(c.below(k), XenomorphMod.RESIN.get().defaultBlockState(), 2);
                    break;
                }
            }
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
