package com.xenomorph;

import com.xenomorph.entity.Caste;
import com.xenomorph.entity.XenoEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Hatches an exploder that follows you and detonates on hostile monsters only (the Creeper Minion Egg of the Mutant Monsters mod, xenomorph style). Max 4 per player. */
public class MinionEgg extends Item {
    public MinionEgg() { super(new Item.Properties().stacksTo(16)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (level instanceof ServerLevel sl) {
            long mine = sl.getEntitiesOfClass(XenoEntity.class, p.getBoundingBox().inflate(64), x -> p.getUUID().equals(x.owner)).size();
            if (mine >= 4) { p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenomorph.minion_limit"), true); return InteractionResultHolder.fail(st); }
            XenoEntity m = XenomorphMod.XENOS.get(Caste.EXPLODER).get().create(sl);
            if (m != null) {
                m.moveTo(p.getX() + p.getLookAngle().x * 1.5, p.getY(), p.getZ() + p.getLookAngle().z * 1.5, p.getYRot(), 0);
                m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.SPAWN_EGG, null, null);
                m.owner = p.getUUID(); m.setPersistenceRequired(); sl.addFreshEntity(m);
                if (!p.getAbilities().instabuild) st.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
