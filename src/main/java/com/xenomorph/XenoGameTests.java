package com.xenomorph;

import com.xenomorph.entity.*;
import com.xenomorph.Hive;
import com.xenomorph.XenoArmor;
import com.xenomorph.XenoConfig;
import com.xenomorph.HiveData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;

/** Server-side smoke tests: every caste performs every attack against a villager without error. */
@GameTestHolder(XenomorphMod.ID)
public class XenoGameTests {
    @GameTest(template = "empty", timeoutTicks = 2100, batch = "b_allAttacks")
    public static void allAttacks(GameTestHelper h) {
        XenoConfig.ENABLE_CEILING_AMBUSH.set(false);      // a stray ceiling nearby would make xenos hang and drop mid-test
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        vill.setInvulnerable(true);
        int[] idx = {0};
        for (Caste c : Caste.values()) {
            XenoEntity x = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(c).get(), new BlockPos(2, 2, 2));
            x.setTarget(vill);
            x.setNoAi(true);
            int k = 0;
            for (Attack a : c.attacks) {
                int at = 20 + k++ * 80;
                h.runAfterDelay(at, () -> { x.setPos(vill.getX() - 3, vill.getY(), vill.getZ()); x.startAttack(a); x.setTarget(vill); });
                h.runAfterDelay(at + a.duration + 2, () -> { if (x.isAlive() && a != Attack.SHADOW_STRIKE && x.attack() != Attack.NONE) throw new AssertionError(c + " stuck in " + a + ": now=" + x.attack() + " tick=" + x.attackTick + " removed=" + x.isRemoved() + " pos=" + x.position() + " vill=" + vill.position() + " tc=" + x.tickCount + " loaded=" + x.level().isLoaded(x.blockPosition()) + " noai=" + x.isNoAi() + " hang=" + x.isHanging() + " hp=" + x.getHealth() + " inv=" + x.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY) + " gt=" + x.level().getGameTime()); });
            }
            idx[0] += 0;
            x.setInvulnerable(true);
            h.runAfterDelay(2090, x::discard);
        }
        var f = h.spawnWithNoFreeWill(XenomorphMod.FACEHUGGER.get(), new BlockPos(1, 2, 1));
        h.runAfterDelay(2095, () -> { XenoConfig.ENABLE_CEILING_AMBUSH.set(true); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_hive")
    public static void hive(GameTestHelper h) {
        var sl = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 2, 4));
        int cy = o.getY() + 8; int cx = (o.getX() >> 4) * 16 + 8, cz = (o.getZ() >> 4) * 16 + 8;
        for (int dx = -8; dx <= 8; dx++) for (int dy = -8; dy <= 8; dy++) for (int dz = -8; dz <= 8; dz++) sl.setBlock(new BlockPos(cx + dx, cy + dy, cz + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        Hive.build(sl, o.getX() >> 4, o.getZ() >> 4, o.getY() + 8);
        if (HiveData.get(sl).hives.isEmpty()) throw new AssertionError("built hive was not recorded");
        long xenos = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEntity.class), x -> true).size();
        long eggs = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEggEntity.class), x -> true).size();
        h.runAfterDelay(2, () -> { if (xenos < 3 || eggs < 3) throw new AssertionError("hive empty x=" + xenos + " e=" + eggs); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_factions")
    public static void factions(GameTestHelper h) {
        var husk = h.spawn(EntityType.HUSK, new BlockPos(2, 2, 2));
        var drone = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(6, 2, 2));
        var pb = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.PATHOGEN_BRUTE).get(), new BlockPos(6, 2, 7));
        h.succeedWhen(() -> {
            if (!(husk.getTarget() instanceof XenoEntity)) throw new net.minecraft.gametest.framework.GameTestAssertException("husk not hostile to xeno");
            if (drone.getTarget() == null || drone.friendly(pb)) throw new net.minecraft.gametest.framework.GameTestAssertException("drone not hostile");
        });
    }

    private static void floor(GameTestHelper h) { for (int x = -6; x < 24; x++) for (int z = -2; z < 12; z++) h.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE); }

    /** A brute told to charge must cover ground, on open floor. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "b_charge")
    public static void charge(GameTestHelper h) {
        floor(h);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(16, 2, 4)); vill.setInvulnerable(true);
        var b = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.BRUTE).get(), new BlockPos(0, 2, 4));
        h.runAfterDelay(10, () -> { b.setTarget(vill); b.startAttack(Attack.CHARGE); });
        net.minecraft.world.phys.Vec3[] st = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(11, () -> st[0] = b.position());
        h.runAfterDelay(50, () -> { if (b.position().distanceTo(st[0]) < 1.5) throw new AssertionError("brute did not charge: moved " + b.position().distanceTo(st[0])); h.succeed(); });
    }

    /** A hugger ignores an already-implanted host; xenos do not hurt that host; neomorph vs xeno fight. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "b_embryoAndNeomorph")
    public static void embryoAndNeomorph(GameTestHelper h) {
        floor(h);
        var host = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        host.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.EMBRYO.get(), 2400, 0));
        var hug = (FacehuggerEntity) h.spawn(XenomorphMod.FACEHUGGER.get(), new BlockPos(2, 2, 4));
        var drone = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(6, 2, 4));
        var neo = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.NEOMORPH).get(), new BlockPos(6, 2, 8));
        h.runAfterDelay(80, () -> {
            if (hug.getTarget() == host) throw new AssertionError("hugger still targets implanted host");
            if (drone.getTarget() == host) throw new AssertionError("xeno still targets implanted host");
            if (drone.friendly(neo)) throw new AssertionError("drone and neomorph are friends");
            var proto = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.PROTOMORPH).get(), new BlockPos(7, 2, 6));
            var pb2 = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.PATHOGEN_RUNNER).get(), new BlockPos(7, 2, 5));
            if (pb2.friendly(neo)) throw new AssertionError("pathogen and neomorph must be hostile");
            var deacon = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.DEACON).get(), new BlockPos(7, 2, 7));
            if (deacon.friendly(neo) || deacon.friendly(drone) || deacon.friendly(proto)) throw new AssertionError("deacon must be its own faction");
            if (drone.friendly(proto) || neo.friendly(proto)) throw new AssertionError("protomorph must be its own faction");
            h.succeed();
        });
    }

    /** Regression: victim's retaliation kills the grabber mid-hit (die() clears the held target) -> used to NPE in tickGrab. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "b_grabberKilledMidGrab")
    public static void grabberKilledMidGrab(GameTestHelper h) {
        floor(h);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        var q = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.QUEEN).get(), new BlockPos(2, 2, 4));
        q.setTarget(vill);
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> l = e -> { if (e.getSource().getEntity() instanceof XenoEntity x) x.kill(); };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(l);
        h.runAfterDelay(5, () -> q.startAttack(Attack.GRAB));
        h.runAfterDelay(100, () -> { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(l); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_everyCasteSpawnsNaturally")
    public static void everyCasteSpawnsNaturally(GameTestHelper h) {
        for (Caste c : Caste.values()) if (!Hive.reachable().contains(c)) throw new AssertionError(c + " cannot spawn naturally");
        h.succeed();
    }

    /** Predator host -> Predalien form, Engineer host -> Deacon; predator hunts xenos. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_hostForms")
    public static void hostForms(GameTestHelper h) {
        floor(h);
        var pred = (HunterEntity) h.spawnWithNoFreeWill(XenomorphMod.HUNTERS.get(HunterEntity.Kind.PREDATOR).get(), new BlockPos(2, 2, 2));
        var eng = (HunterEntity) h.spawnWithNoFreeWill(XenomorphMod.HUNTERS.get(HunterEntity.Kind.ENGINEER).get(), new BlockPos(2, 2, 6));
        pred.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.EMBRYO.get(), 4, 0));
        eng.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.EMBRYO.get(), 4, 0));
        h.succeedWhen(() -> {
            var all = h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ChestbursterEntity.class), b -> true);
            boolean p = all.stream().anyMatch(b -> b.form == Caste.PREDALIEN || b.form == Caste.BERSERKER), d = all.stream().anyMatch(b -> b.form == Caste.DEACON || b.form == Caste.ULTRAMORPH);
            if (!p || !d) throw new net.minecraft.gametest.framework.GameTestAssertException("forms predalien=" + p + " deacon=" + d);
        });
    }

    /** A xeno must scale a wall to reach prey standing on a ledge. */
    @GameTest(template = "empty", timeoutTicks = 480, batch = "b_climbsWalls")
    public static void climbsWalls(GameTestHelper h) {
        floor(h);
        for (int y = 2; y <= 9; y++) for (int z = -2; z < 12; z++) h.setBlock(new BlockPos(8, y, z), net.minecraft.world.level.block.Blocks.STONE);
        for (int x = 8; x < 14; x++) for (int z = -2; z < 12; z++) h.setBlock(new BlockPos(x, 10, z), net.minecraft.world.level.block.Blocks.STONE);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(11, 11, 4)); vill.setInvulnerable(true);
        var d = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(3, 2, 4));
        h.onEachTick(() -> { d.setTarget(vill); d.getMoveControl().setWantedPosition(vill.getX(), vill.getY(), vill.getZ(), 1.3); });      // other tests share the world: keep target and heading pinned
        h.succeedWhen(() -> { if (d.getY() < h.absolutePos(new BlockPos(0, 9, 0)).getY()) throw new net.minecraft.gametest.framework.GameTestAssertException("not climbing, pos=" + d.position() + " climbing=" + d.isClimbing() + " target=" + d.getTarget() + " org=" + h.absolutePos(new BlockPos(0, 0, 0)) + " vill=" + vill.position() + " hcol=" + d.horizontalCollision); });
    }

    /** Ceiling ambush: hangs, creeps toward prey, drops. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_ceilingAmbush")
    public static void ceilingAmbush(GameTestHelper h) {
        XenoConfig.ENABLE_CEILING_AMBUSH.set(true);
        floor(h);
        for (int x = -4; x < 22; x++) for (int z = -2; z < 12; z++) h.setBlock(new BlockPos(x, 6, z), net.minecraft.world.level.block.Blocks.STONE);
        var d = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(2, 2, 4));
        if (!d.tryHang()) throw new AssertionError("could not hang");
        double y0 = d.getY();
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(12, 2, 4)); vill.setInvulnerable(true);
        h.runAfterDelay(5, () -> { if (!d.isHanging()) throw new AssertionError("fell immediately"); });
        h.succeedWhen(() -> { if (d.isHanging() || d.getY() > y0 - 1.0) throw new net.minecraft.gametest.framework.GameTestAssertException("still hanging; hang=" + d.isHanging()); });
    }

    /** Red strain: own faction, hostile to black xenos, red embryo in a large host births a Red Queen Mother. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_red")
    public static void redStrain(GameTestHelper h) {
        floor(h);
        var rw = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.RED_WARRIOR).get(), new BlockPos(1, 2, 1));
        var bd = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(6, 2, 1));
        if (rw.friendly(bd)) throw new AssertionError("red and black must be hostile");
        var pred = (HunterEntity) h.spawnWithNoFreeWill(XenomorphMod.HUNTERS.get(HunterEntity.Kind.PREDATOR).get(), new BlockPos(2, 2, 6));
        pred.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.RED_EMBRYO.get(), 4, 0));
        h.succeedWhen(() -> {
            boolean q = h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ChestbursterEntity.class), b -> b.form == Caste.RED_QUEEN_MOTHER).size() > 0;
            if (!q) throw new net.minecraft.gametest.framework.GameTestAssertException("no red queen burster");
        });
    }

    /** Rogue King hunts queens; K-series shrug off acid; new factions are separate. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "b_kings")
    public static void kingsAndKSeries(GameTestHelper h) {
        floor(h);
        var king = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.KING).get(), new BlockPos(1, 2, 1));
        var queen = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.QUEEN).get(), new BlockPos(8, 2, 1));
        var k = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.K_WARRIOR).get(), new BlockPos(1, 2, 7));
        var red = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.RED_WARRIOR).get(), new BlockPos(2, 2, 7));
        var clone = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.CLONE_XENOMORPH).get(), new BlockPos(3, 2, 7));
        var wh = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.WHITE_HYBRID).get(), new BlockPos(4, 2, 7));
        var tk = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.TRUE_KING).get(), new BlockPos(5, 2, 7));
        var dr = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(6, 2, 7));
        if (!tk.friendly(dr)) throw new AssertionError("true king belongs to the classic hive");
        var nb = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.NEWBORN).get(), new BlockPos(7, 2, 7));
        if (king.friendly(queen) || clone.friendly(wh) || k.friendly(clone) || k.friendly(red) || nb.friendly(clone)) throw new AssertionError("factions merged");
        h.runAfterDelay(2, () -> {
            float hp = k.getHealth();
            red.hurt(h.getLevel().damageSources().mobAttack(k), 1f);          // k hits red: red's acid must not hurt k
            h.runAfterDelay(2, () -> { if (k.getHealth() < hp) throw new AssertionError("K-series was hurt by acid"); });
        });
        h.runAfterDelay(90, () -> { if (!(king.getTarget() == queen)) throw new AssertionError("rogue king not hunting the queen: " + king.getTarget()); h.succeed(); });
    }

    /** K/Clone strains have a full ladder, with a queen that lays and a hive of their own. */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_ladders")
    public static void strainLadders(GameTestHelper h) {
        for (Caste c : new Caste[]{Caste.K_DRONE, Caste.K_PRAETORIAN, Caste.K_QUEEN, Caste.CLONE_WARRIOR, Caste.CLONE_PRAETORIAN, Caste.CLONE_QUEEN})
            if (!Hive.reachable().contains(c)) throw new AssertionError(c + " unreachable");
        if (!Caste.K_QUEEN.attacks.contains(Attack.LAY) || !Caste.CLONE_QUEEN.attacks.contains(Attack.LAY)) throw new AssertionError("queens must lay");
        h.succeed();
    }

    /** DNA reflex: cow -> Bull Alien, ocelot -> Panther Alien, polar bear -> Winterbeast. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_dnaReflex")
    public static void dnaReflex(GameTestHelper h) {
        floor(h);
        var cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        var cat = h.spawnWithNoFreeWill(EntityType.OCELOT, new BlockPos(2, 2, 5));
        var bear = h.spawnWithNoFreeWill(EntityType.POLAR_BEAR, new BlockPos(2, 2, 8));
        for (var e : new net.minecraft.world.entity.LivingEntity[]{cow, cat, bear}) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.EMBRYO.get(), 4, 0));
        h.succeedWhen(() -> {
            var all = h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ChestbursterEntity.class), b -> true);
            boolean ok = all.stream().anyMatch(b -> b.form == Caste.BULL_ALIEN) && all.stream().anyMatch(b -> b.form == Caste.PANTHER) && all.stream().anyMatch(b -> b.form == Caste.WINTERBEAST);
            if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException("host forms not found: " + all.stream().map(b -> b.form).toList());
        });
    }

    /** Full acid-neutralizing suit is recognised; a wounded villager struck by a xeno is cocooned instead of killed. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_suitCocoon")
    public static void suitAndCocoon(GameTestHelper h) {
        floor(h);
        var z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 7));
        var slots = net.minecraft.world.entity.EquipmentSlot.values();
        String[] n = {"boots", "leggings", "chestplate", "helmet"};
        var reg = net.minecraftforge.registries.ForgeRegistries.ITEMS;
        for (int i = 0; i < 4; i++) z.setItemSlot(slots[2 + i], new net.minecraft.world.item.ItemStack(reg.getValue(new net.minecraft.resources.ResourceLocation("xenomorph", "acidproof_" + n[i]))));
        if (XenoArmor.pieces(z) != 4) throw new AssertionError("suit pieces=" + XenoArmor.pieces(z));
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 2)); vill.setHealth(3f);
        var d = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(2, 2, 2));
        h.onEachTick(() -> d.setTarget(vill));
        h.runAfterDelay(3, () -> { d.setPos(vill.getX() - 1.2, vill.getY(), vill.getZ()); d.startAttack(Attack.CLAW_L); });
        h.succeedWhen(() -> { if (!vill.hasEffect(XenomorphMod.EMBRYO.get())) throw new net.minecraft.gametest.framework.GameTestAssertException("villager not cocooned"); });
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "b_surgery")
    public static void surgery(GameTestHelper h) {
        floor(h);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
        vill.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.EMBRYO.get(), 2000, 0));
        var p = h.makeMockPlayer();
        var kit = new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("xenomorph", "surgical_kit")));
        kit.getItem().interactLivingEntity(kit, p, vill, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (vill.hasEffect(XenomorphMod.EMBRYO.get())) throw new AssertionError("embryo not removed");
        if (!vill.isAlive()) throw new AssertionError("patient died");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_praeto")
    public static void praetoEmbryo(GameTestHelper h) {
        floor(h);
        var v = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(XenomorphMod.PRAETO_EMBRYO.get(), 4, 0));
        h.succeedWhen(() -> { if (h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ChestbursterEntity.class), b -> b.form == Caste.STALKER).isEmpty()) throw new net.minecraft.gametest.framework.GameTestAssertException("no stalker burster"); });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_shadowAlone")
    public static void shadowAlone(GameTestHelper h) {
        floor(h);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 4)); vill.setInvulnerable(true);
        var x = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.WHITE_HYBRID).get(), new BlockPos(2, 2, 4));
        x.setNoAi(true); x.setTarget(vill);
        h.runAfterDelay(5, () -> x.startAttack(Attack.SHADOW_STRIKE));
        h.runAfterDelay(50, () -> { if (x.attack() != Attack.NONE) throw new AssertionError("stuck alone: tick=" + x.attackTick + " tc=" + x.tickCount); h.succeed(); });
    }

    /** A hive with three guards raids a faraway Warden-class boss (stand-in for any mod boss): raiders spawn near it and are locked on. */
    @GameTest(template = "empty", timeoutTicks = 60, batch = "b_raid")
    public static void raidHitsBoss(GameTestHelper h) {
        var sl = h.getLevel();
        BlockPos hive = h.absolutePos(new BlockPos(2, 2, 2)), far = hive.offset(36, 0, 0);
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) sl.getChunkAt(far.offset(cx * 16, 0, cz * 16));   // raiders spawn up to 34 blocks around the boss: those chunks must be loaded
        sl.getChunkAt(hive);
        for (int dx = -40; dx <= 40; dx++) for (int dz = -40; dz <= 40; dz++) { sl.setBlock(far.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2); for (int dy = 0; dy < 4; dy++) sl.setBlock(far.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2); } sl.getChunkSource().getChunk(far.getX() >> 4, far.getZ() >> 4, true);   // a platform for the raiders to spawn on
        for (int i = 0; i < 3; i++) { var g = XenomorphMod.XENOS.get(Caste.DRONE).get().create(sl); g.moveTo(hive.getX() + i, hive.getY(), hive.getZ(), 0, 0); g.setNoAi(true); sl.addFreshEntity(g); }
        var boss = EntityType.WARDEN.create(sl); boss.moveTo(far.getX() + .5, far.getY(), far.getZ() + .5, 0, 0); boss.setNoAi(true); boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000); sl.addFreshEntity(boss);   // outranks any other test's leftovers
        String big = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(hive).inflate(160), e -> e.getMaxHealth() >= 500).stream().map(e -> e.getType().toShortString() + ":" + e.getMaxHealth() + "@" + e.blockPosition().toShortString()).toList().toString();
        int sent = Hive.raidOne(sl, new HiveData.Entry(hive, "classic"), new java.util.Random(1), true);
        var raiders = sl.getEntitiesOfClass(XenoEntity.class, new net.minecraft.world.phys.AABB(far).inflate(45), x -> x.getTarget() == boss);
        boolean alive = boss.isAlive(); String near = sl.getEntitiesOfClass(XenoEntity.class, new net.minecraft.world.phys.AABB(far).inflate(45)).stream().map(x -> x.caste + "->" + (x.getTarget() == null ? null : x.getTarget().getType().toShortString())).toList().toString();
        String dbg = sl.getEntitiesOfClass(XenoEntity.class, new net.minecraft.world.phys.AABB(hive).inflate(200)).stream().map(x -> x.caste + "@" + x.blockPosition().toShortString() + "->" + x.getTarget()).toList() + " boss@" + boss.blockPosition().toShortString() + " hive=" + hive.toShortString();
        sl.getEntitiesOfClass(XenoEntity.class, new net.minecraft.world.phys.AABB(hive).inflate(200)).forEach(x -> x.discard()); boss.discard();
        if (sent < 3 || raiders.size() < 3) throw new AssertionError("sent=" + sent + " raiders=" + raiders.size()  + " alive=" + alive + " near=" + near + " big=" + big + " bossHp=" + boss.getMaxHealth());
        h.succeed();
    }

    /** A worker next to bare stone that touches resin coats it with resin (goal driven by hand so wandering and shared-world leftovers cannot interfere). */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "b_worker")
    public static void workerBuildsResin(GameTestHelper h) {
        floor(h);
        for (int y = 2; y <= 3; y++) for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(6, y, z), net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(new BlockPos(6, 2, 6), XenomorphMod.RESIN.get());
        var w = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.WORKER).get(), new BlockPos(4, 2, 4));
        w.setTarget(null);
        var goal = new com.xenomorph.entity.WorkerResinGoal(w);
        boolean started = false;
        for (int i = 0; i < 60 && !started; i++) if (goal.canUse()) { goal.start(); started = true; }
        if (!started) throw new net.minecraft.gametest.framework.GameTestAssertException("worker found no job next to resin");
        for (int i = 0; i < 70 && goal.canContinueToUse(); i++) { var st = goal.standPos(); w.setPos(st.getX() + .5, st.getY(), st.getZ() + .5); goal.tick(); }
        w.discard();
        h.assertBlock(new BlockPos(6, 2, 5), b -> b == XenomorphMod.RESIN.get(), () -> "stone next to the resin was not coated");
        h.succeed();
    }

    /** The tunnel from a buried hive climbs through solid rock to open sky: resin floor, clear 3x4 passage. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "b_tunnel")
    public static void hiveTunnelReachesSurface(GameTestHelper h) {
        var sl = h.getLevel();
        BlockPos core = h.absolutePos(new BlockPos(4, 2, 4)).above(4);
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int d = 0; d < 4; d++) for (int i = -3; i < 40; i++) for (int w = -3; w <= 3; w++) for (int y = core.getY() - 6; y <= core.getY() + 10; y++) {
            int dx = d == 0 ? i : d == 1 ? -i : w, dz = d == 2 ? i : d == 3 ? -i : w;
            sl.setBlock(core.offset(dx, y - core.getY(), dz), stone, 2);
        }
        Hive.tunnel(sl, core, (x, z) -> core.getY() + 10);   // the shared test world has no real terrain heightmap, so the surface is injected
        BlockPos mid = core.offset(10, -4 + 6, 0);           // 10 blocks along +x the floor has risen 6
        if (!sl.getBlockState(mid.below()).is(XenomorphMod.RESIN.get())) throw new AssertionError("no resin floor in tunnel at " + mid.below() + " core=" + core + " surfaceE10=" + sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, core.getX() + 10, core.getZ()) + " states: " + sl.getBlockState(mid.below()) + " / " + sl.getBlockState(mid.below().above()) + " / " + sl.getBlockState(mid.below(2)));
        for (int y = 0; y < 4; y++) if (!sl.getBlockState(mid.above(y)).isAir()) throw new AssertionError("tunnel blocked at height " + y);
        if (!sl.getBlockState(core.offset(18, 10, 0)).isAir()) throw new AssertionError("tunnel does not reach the top");
        h.succeed();
    }

    /** Mutant lineage passives: blaze shield layers soak damage, the Blast xeno shrugs off explosions, hoglin rage kicks in after rapid hits. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "b_mutantPassives")
    public static void mutantPassives(GameTestHelper h) {
        var attacker = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var blaze = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.BLAZE_XENO).get(), new BlockPos(3, 2, 3));
        float hp = blaze.getHealth(); blaze.hurt(blaze.damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) attacker), 20f);
        if (blaze.getHealth() < hp) throw new AssertionError("blaze shield did not absorb 20 damage");
        var blast = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.BLAST_XENO).get(), new BlockPos(5, 2, 3));
        hp = blast.getHealth(); blast.hurt(blast.damageSources().explosion(null, null), 50f);
        if (blast.getHealth() < hp) throw new AssertionError("blast xeno took explosion damage");
        var hog = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.HOG_XENO).get(), new BlockPos(7, 2, 3));
        for (int i = 0; i < 4; i++) hog.hurt(hog.damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) attacker), 1f);
        if (!hog.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)) throw new AssertionError("hoglin xeno did not enrage");
        blaze.discard(); blast.discard(); hog.discard(); attacker.discard();
        h.succeed();
    }

    /** A tamed exploder never targets its owner or villagers, but will target a hostile monster. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_minion")
    public static void tamedMinion(GameTestHelper h) {
        var owner = h.makeMockPlayer(); var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 2, 2)); var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        var m = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.EXPLODER).get(), new BlockPos(1, 2, 1));
        m.owner = owner.getUUID();
        if (m.canAttack(owner) || m.canAttack(vill)) throw new AssertionError("tamed minion would attack its owner or a villager");
        if (!m.canAttack(zombie)) throw new AssertionError("tamed minion will not attack a zombie");
        m.discard(); vill.discard(); zombie.discard();
        h.succeed();
    }

    /** The full Bone armour set cuts projectile damage by 40% on top of the armour itself (compared with the same set minus the boots). */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_boneArmor")
    public static void boneArmorSetBonus(GameTestHelper h) {
        var slots = new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        float[] lost = new float[2];
        for (int k = 0; k < 2; k++) {
            var v = (net.minecraft.world.entity.LivingEntity) h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2 + k * 2, 2, 2));
            for (int i = 0; i < (k == 0 ? 4 : 3); i++) v.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(XenomorphMod.BONE_SET.get(i).get()));
            float hp = v.getHealth(); v.hurt(v.damageSources().arrow(null, null), 10f);
            lost[k] = hp - v.getHealth(); v.discard();
        }
        if (!(lost[0] < lost[1] * 0.8f)) throw new AssertionError("no set bonus: full=" + lost[0] + " minus boots=" + lost[1]);
        h.succeed();
    }

    /** Lightning charges (does not hurt) a Blast xenomorph. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_charged")
    public static void lightningChargesBlast(GameTestHelper h) {
        var x = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.BLAST_XENO).get(), new BlockPos(2, 2, 2));
        x.setHealth(40f);
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel()); bolt.moveTo(x.getX(), x.getY(), x.getZ()); bolt.setVisualOnly(false); h.getLevel().addFreshEntity(bolt);
        h.runAfterDelay(3, () -> {
            boolean ok = x.getHealth() >= x.getMaxHealth() - 0.5f && x.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
            x.discard(); bolt.discard();
            if (!ok) throw new AssertionError("blast xeno was not charged by lightning");
            h.succeed();
        });
    }

    /** Mutagen mapping: vanilla creatures map to their xenomorph mutant; queens, players and ordinary animals do not mutate. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_mutagen")
    public static void mutagenMapping(GameTestHelper h) {
        var cr = h.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(2, 2, 2)); var en = h.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(3, 2, 2));
        var cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 2)); var q = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.QUEEN).get(), new BlockPos(5, 2, 2));
        var dr = (XenoEntity) h.spawnWithNoFreeWill(XenomorphMod.XENOS.get(Caste.DRONE).get(), new BlockPos(6, 2, 2));
        boolean ok = Mutagen.mutantFor((net.minecraft.world.entity.LivingEntity) cr) == Caste.BLAST_XENO && Mutagen.mutantFor((net.minecraft.world.entity.LivingEntity) en) == Caste.VOID_XENO
                && Mutagen.mutantFor((net.minecraft.world.entity.LivingEntity) cow) == null && Mutagen.mutantFor(q) == null && Mutagen.mutantFor(dr) != null;
        cr.discard(); en.discard(); cow.discard(); q.discard(); dr.discard();
        if (!ok) throw new AssertionError("mutagen mapping wrong");
        h.succeed();
    }
}
