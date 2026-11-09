package com.xenomorph;

import com.xenomorph.entity.*;
import com.xenomorph.Hive;
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
    @GameTest(template = "empty", timeoutTicks = 2100)
    public static void allAttacks(GameTestHelper h) {
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
                h.runAfterDelay(at + a.duration + 2, () -> { if (x.isAlive() && x.attack() != Attack.NONE) throw new AssertionError(c + " stuck in " + a); });
            }
            idx[0] += 0;
            x.setInvulnerable(true);
            h.runAfterDelay(2090, x::discard);
        }
        var f = h.spawnWithNoFreeWill(XenomorphMod.FACEHUGGER.get(), new BlockPos(1, 2, 1));
        h.runAfterDelay(2095, h::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hive(GameTestHelper h) {
        var sl = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 2, 4));
        int cy = o.getY() + 8; int cx = (o.getX() >> 4) * 16 + 8, cz = (o.getZ() >> 4) * 16 + 8;
        for (int dx = -8; dx <= 8; dx++) for (int dy = -8; dy <= 8; dy++) for (int dz = -8; dz <= 8; dz++) sl.setBlock(new BlockPos(cx + dx, cy + dy, cz + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        Hive.build(sl, o.getX() >> 4, o.getZ() >> 4, o.getY() + 8);
        long xenos = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEntity.class), x -> true).size();
        long eggs = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEggEntity.class), x -> true).size();
        h.runAfterDelay(2, () -> { if (xenos < 3 || eggs < 3) throw new AssertionError("hive empty x=" + xenos + " e=" + eggs); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
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
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void charge(GameTestHelper h) {
        floor(h);
        var vill = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(16, 2, 4)); vill.setInvulnerable(true);
        var b = (XenoEntity) h.spawn(XenomorphMod.XENOS.get(Caste.BRUTE).get(), new BlockPos(0, 2, 4));
        h.runAfterDelay(10, () -> { b.setTarget(vill); b.startAttack(Attack.CHARGE); });
        net.minecraft.world.phys.Vec3[] st = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(11, () -> st[0] = b.position());
        h.runAfterDelay(50, () -> { if (b.position().distanceTo(st[0]) < 6) throw new AssertionError("brute did not charge: moved " + b.position().distanceTo(st[0])); h.succeed(); });
    }

    /** A hugger ignores an already-implanted host; xenos do not hurt that host; neomorph vs xeno fight. */
    @GameTest(template = "empty", timeoutTicks = 120)
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
            h.succeed();
        });
    }

    /** Regression: victim's retaliation kills the grabber mid-hit (die() clears the held target) -> used to NPE in tickGrab. */
    @GameTest(template = "empty", timeoutTicks = 120)
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
}
