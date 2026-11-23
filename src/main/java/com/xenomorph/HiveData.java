package com.xenomorph;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Every hive that actually got built (position + leader caste id), so commands can find them. */
public class HiveData extends SavedData {
    public record Entry(BlockPos pos, String kind) {}
    public final List<Entry> hives = new ArrayList<>();
    public final java.util.Set<Long> tunneled = new java.util.HashSet<>();   // hive cores that already have their way to the surface
    public boolean isTunneled(BlockPos p) { return tunneled.contains(p.asLong()); }
    public void markTunneled(BlockPos p) { tunneled.add(p.asLong()); setDirty(); }

    public static HiveData get(ServerLevel l) { return l.getDataStorage().computeIfAbsent(HiveData::load, HiveData::new, "xenomorph_hives"); }
    public void add(BlockPos p, String kind) { hives.add(new Entry(p, kind)); setDirty(); }

    public static HiveData load(CompoundTag t) {
        HiveData d = new HiveData();
        for (Tag e : t.getList("Hives", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) e; d.hives.add(new Entry(new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")), c.getString("Kind"))); }
        for (long l : t.getLongArray("TunneledV3")) d.tunneled.add(l);
        return d;
    }
    @Override public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Entry e : hives) { CompoundTag c = new CompoundTag(); c.putInt("X", e.pos.getX()); c.putInt("Y", e.pos.getY()); c.putInt("Z", e.pos.getZ()); c.putString("Kind", e.kind); l.add(c); }
        t.put("Hives", l);
        t.putLongArray("TunneledV3", tunneled.stream().mapToLong(Long::longValue).toArray());
        return t;
    }
}
