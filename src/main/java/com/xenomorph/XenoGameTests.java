package com.xenomorph;

import com.xenomorph.entity.*;
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
    @GameTest(template = "empty", timeoutTicks = 1400)
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
                h.runAfterDelay(at + a.duration + 2, () -> { if (x.attack() != Attack.NONE) throw new AssertionError(c + " stuck in " + a); });
            }
            idx[0] += 0;
            x.setInvulnerable(true);
            h.runAfterDelay(1190, x::discard);
        }
        var f = h.spawnWithNoFreeWill(XenomorphMod.FACEHUGGER.get(), new BlockPos(1, 2, 1));
        h.runAfterDelay(1195, h::succeed);
    }
}
