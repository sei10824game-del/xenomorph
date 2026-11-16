package com.xenomorph;

import com.xenomorph.entity.XenoEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/** Right-click: pings every xeno within 48 blocks and shows the nearest one's distance and bearing. */
public class MotionTracker extends Item {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public MotionTracker() { super(new Item.Properties().stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide) {
            List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48, 24, 48), e -> XenoEntity.faction(e) != 0 && XenoEntity.faction(e) != 3 && e.isAlive());
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1f, hits.isEmpty() ? 0.6f : 1.6f);
            if (hits.isEmpty()) p.displayClientMessage(Component.literal("Motion tracker: no contacts"), true);
            else {
                LivingEntity n = hits.stream().min(Comparator.comparingDouble(p::distanceToSqr)).get();
                double dx = n.getX() - p.getX(), dz = n.getZ() - p.getZ();
                float rel = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - p.getYRot());
                int dir = Math.floorMod(Math.round(rel / 45f), 8);
                p.displayClientMessage(Component.literal("Motion tracker: " + hits.size() + " contact(s) - nearest " + (int) p.distanceTo(n) + "m " + ARROWS[dir] + (n.getY() - p.getY() > 3 ? " (above)" : n.getY() - p.getY() < -3 ? " (below)" : "")), true);
            }
            p.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
