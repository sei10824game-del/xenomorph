package com.xenomorph;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** /xenomorph hive [list|tp] - find generated hives (op only, like /locate). */
@Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class XenoCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("xenomorph").requires(s -> s.hasPermission(2)).then(Commands.literal("hive")
                .executes(c -> nearest(c.getSource(), false))
                .then(Commands.literal("tp").executes(c -> nearest(c.getSource(), true)))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))));
    }

    private static List<HiveData.Entry> sorted(CommandSourceStack s) {
        BlockPos o = BlockPos.containing(s.getPosition());
        return HiveData.get(s.getLevel()).hives.stream().sorted(Comparator.comparingDouble(h -> h.pos().distSqr(o))).toList();
    }
    private static String name(String kind) { return Component.translatable("entity.xenomorph." + kind).getString(); }

    private static int nearest(CommandSourceStack s, boolean tp) {
        List<HiveData.Entry> l = sorted(s);
        if (l.isEmpty()) { s.sendFailure(Component.literal("No hive has been generated in this dimension yet (hives appear in newly generated overworld chunks).")); return 0; }
        HiveData.Entry h = l.get(0);
        int dist = (int) Math.sqrt(h.pos().distSqr(BlockPos.containing(s.getPosition())));
        if (tp) {
            try { s.getPlayerOrException().teleportTo(s.getLevel(), h.pos().getX() + .5, h.pos().getY() - 2, h.pos().getZ() + .5, s.getPlayerOrException().getYRot(), 0); }
            catch (Exception ex) { s.sendFailure(Component.literal("Only players can teleport.")); return 0; }
        }
        s.sendSuccess(() -> Component.literal("Nearest hive (" + name(h.kind()) + "): " + h.pos().getX() + " " + h.pos().getY() + " " + h.pos().getZ() + "  - " + dist + " blocks away"), false);
        return 1;
    }

    private static int list(CommandSourceStack s) {
        List<HiveData.Entry> l = sorted(s);
        s.sendSuccess(() -> Component.literal(l.size() + " hive(s) found:"), false);
        for (HiveData.Entry h : l.stream().limit(15).toList())
            s.sendSuccess(() -> Component.literal(" - " + name(h.kind()) + "  " + h.pos().getX() + " " + h.pos().getY() + " " + h.pos().getZ()), false);
        return l.size();
    }
}
