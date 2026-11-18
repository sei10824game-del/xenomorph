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

    /** Held in a hand it pings by itself: the closer the nearest contact, the faster and higher the beeps (like the film's tracker). */
    @Override public void inventoryTick(ItemStack st, Level level, net.minecraft.world.entity.Entity ent, int slot, boolean selected) {
        if (level.isClientSide || !(ent instanceof Player p) || !(selected || p.getOffhandItem() == st) || p.getCooldowns().isOnCooldown(this)) return;
        var hits = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(30, 16, 30), e -> XenoEntity.faction(e) != 0 && XenoEntity.faction(e) != 3 && e.isAlive());
        if (hits.isEmpty()) return;
        double d = Math.sqrt(hits.stream().mapToDouble(p::distanceToSqr).min().getAsDouble());
        int gap = (int) Mth.clamp(d * 1.6, 6, 50);
        if (level.getGameTime() % gap != 0) return;
        level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, (float) Mth.clamp(2.0 - d / 25.0, 0.8, 2.0));
    }

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
