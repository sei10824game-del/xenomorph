package com.xenomorph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** The Derelict Laboratory: a small ruined surface building (rare, overworld) holding Mutagen and the ingredients to make more. */
@Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class Lab {
    private Lab() {}

    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || !e.isNewChunk() || !XenoConfig.LABS.get()) return;
        int cx = e.getChunk().getPos().x, cz = e.getChunk().getPos().z;
        Random r = new Random(sl.getSeed() ^ (cx * 668265263L + cz * 374761393L));
        if (r.nextInt(220) != 0) return;
        build(sl, cx * 16 + 3, cz * 16 + 3, r);
    }

    /** 9x9 footprint starting at (x0, z0); stays inside one chunk. */
    static void build(ServerLevel sl, int x0, int z0, Random r) {
        int y0 = sl.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x0 + 4, z0 + 4);
        if (!sl.getBlockState(new BlockPos(x0 + 4, y0 - 1, z0 + 4)).getFluidState().isEmpty() || y0 < sl.getSeaLevel()) return;   // not in the water
        buildAt(sl, x0, y0, z0, r);
    }

    /** Floor level y0 is the block layer of the floor itself. */
    static void buildAt(ServerLevel sl, int x0, int y0, int z0, Random r) {
        for (int dx = 0; dx < 9; dx++) for (int dz = 0; dz < 9; dz++) {
            boolean wall = dx == 0 || dz == 0 || dx == 8 || dz == 8;
            for (int dy = -3; dy < 0; dy++) { BlockPos q = new BlockPos(x0 + dx, y0 + dy, z0 + dz); if (sl.getBlockState(q).isAir() || !sl.getBlockState(q).getFluidState().isEmpty()) sl.setBlock(q, Blocks.STONE_BRICKS.defaultBlockState(), 2); }   // foundation
            sl.setBlock(new BlockPos(x0 + dx, y0, z0 + dz), r.nextInt(6) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int dy = 1; dy <= 5; dy++) {
                BlockPos q = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
                if (wall && dy <= 4) {
                    boolean window = dy == 3 && (dx + dz) % 3 == 1 && !(dx == 4 || dz == 4);
                    var b = window ? Blocks.IRON_BARS.defaultBlockState() : r.nextInt(4) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : r.nextInt(5) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    if (r.nextInt(14) != 0) sl.setBlock(q, b, 2); else sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);     // ruined: holes in the walls
                } else if (dy == 5 && r.nextInt(3) != 0) sl.setBlock(q, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);       // partial roof
                else sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        sl.setBlock(new BlockPos(x0 + 4, y0 + 1, z0 + 8), Blocks.AIR.defaultBlockState(), 2); sl.setBlock(new BlockPos(x0 + 4, y0 + 2, z0 + 8), Blocks.AIR.defaultBlockState(), 2);   // doorway
        // fittings: brewing stand and cauldron, a lectern, a chest with the loot
        sl.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
        sl.setBlock(new BlockPos(x0 + 3, y0 + 1, z0 + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        sl.setBlock(new BlockPos(x0 + 6, y0 + 1, z0 + 2), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH), 2);
        BlockPos cp = new BlockPos(x0 + 6, y0 + 1, z0 + 6);
        sl.setBlock(cp, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH), 2);
        if (sl.getBlockEntity(cp) instanceof ChestBlockEntity ch) {
            ch.setItem(1, new ItemStack(XenomorphMod.MUTAGEN.get(), 1 + r.nextInt(2)));
            ch.setItem(4, new ItemStack(Items.FERMENTED_SPIDER_EYE, 1 + r.nextInt(3)));
            ch.setItem(7, new ItemStack(Items.GLOWSTONE_DUST, 3 + r.nextInt(5)));
            ch.setItem(10, new ItemStack(Items.GLASS_BOTTLE, 2 + r.nextInt(4)));
            ch.setItem(13, new ItemStack(XenomorphMod.GLAND.get(), 1 + r.nextInt(2)));
            if (r.nextInt(3) == 0) ch.setItem(16, new ItemStack(XenomorphMod.PEDIA.get()));
            if (r.nextInt(4) == 0) ch.setItem(19, new ItemStack(XenomorphMod.TRACKER.get()));
        }
    }
}
