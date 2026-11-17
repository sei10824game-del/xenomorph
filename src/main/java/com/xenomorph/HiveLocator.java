package com.xenomorph;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;

/** Right-click: bearing and distance to the nearest hive generated in this world. */
public class HiveLocator extends Item {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    public HiveLocator() { super(new Item.Properties().stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            var near = HiveData.get(sl).hives.stream().min(Comparator.comparingDouble(h -> h.pos().distSqr(p.blockPosition())));
            if (near.isEmpty()) p.displayClientMessage(Component.literal("Hive locator: no hive found yet"), true);
            else {
                BlockPos b = near.get().pos();
                double dx = b.getX() + .5 - p.getX(), dz = b.getZ() + .5 - p.getZ();
                float rel = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - p.getYRot());
                p.displayClientMessage(Component.literal("Hive locator: " + ARROWS[Math.floorMod(Math.round(rel / 45f), 8)] + " " + (int) Math.sqrt(dx * dx + dz * dz) + "m, depth y=" + b.getY()), true);
            }
            p.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }
}
