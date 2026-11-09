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
        Hive.build(sl, o.getX() >> 4, o.getZ() >> 4, o.getY() + 8);
        long xenos = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEntity.class), x -> true).size();
        long eggs = sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(XenoEggEntity.class), x -> true).size();
        h.runAfterDelay(2, () -> { if (xenos < 3 || eggs < 1) throw new AssertionError("hive empty x=" + xenos + " e=" + eggs); h.succeed(); });
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
}
