package com.xenomorph;

import com.xenomorph.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mod(XenomorphMod.ID)
public class XenomorphMod {
    public static final String ID = "xenomorph";
    public static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final RegistryObject<net.minecraft.world.level.block.Block> RESIN = BLOCKS.register("resin", () -> new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN).strength(3f, 6f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.HONEY_BLOCK)));
    static final RegistryObject<Item> RESIN_ITEM = ITEMS.register("resin", () -> new net.minecraft.world.item.BlockItem(RESIN.get(), new Item.Properties()));
    static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ID);
    static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final Map<Caste, RegistryObject<EntityType<XenoEntity>>> XENOS = new EnumMap<>(Caste.class);
    public static final Map<HunterEntity.Kind, RegistryObject<EntityType<HunterEntity>>> HUNTERS = new EnumMap<>(HunterEntity.Kind.class);
    public static final RegistryObject<EntityType<FacehuggerEntity>> FACEHUGGER = ENTITIES.register("facehugger",
            () -> EntityType.Builder.of(FacehuggerEntity::new, MobCategory.MONSTER).sized(0.7f, 0.4f).clientTrackingRange(8).build("facehugger"));
    public static final RegistryObject<EntityType<ChestbursterEntity>> BURSTER = ENTITIES.register("chestburster",
            () -> EntityType.Builder.of(ChestbursterEntity::new, MobCategory.MONSTER).sized(0.4f, 0.4f).clientTrackingRange(8).build("chestburster"));
    public static final RegistryObject<EntityType<XenoEggEntity>> EGG = ENTITIES.register("egg",
            () -> EntityType.Builder.<XenoEggEntity>of(XenoEggEntity::new, MobCategory.MISC).sized(0.8f, 1.2f).clientTrackingRange(8).build("egg"));
    public static final RegistryObject<EntityType<AcidSpit>> SPIT = ENTITIES.register("acid_spit",
            () -> EntityType.Builder.<AcidSpit>of(AcidSpit::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("acid_spit"));
    public static final RegistryObject<MobEffect> EMBRYO = EFFECTS.register("embryo", () -> new EmbryoEffect(false));
    public static final RegistryObject<MobEffect> RED_EMBRYO = EFFECTS.register("red_embryo", () -> new EmbryoEffect(true));
    static final List<RegistryObject<Item>> EGGS = new ArrayList<>();

    static {
        for (Caste c : Caste.values()) {
            XENOS.put(c, ENTITIES.register(c.id, () -> EntityType.Builder.<XenoEntity>of((t, l) -> new XenoEntity(t, l, c), MobCategory.MONSTER)
                    .sized(0.8f * c.scale, 2.1f * c.scale).clientTrackingRange(10).fireImmune().build(c.id)));
            EGGS.add(ITEMS.register(c.id + "_spawn_egg", () -> new ForgeSpawnEggItem(XENOS.get(c), 0x14181c, 0x3a5a4a + c.ordinal() * 0x050301, new Item.Properties())));
        }
        for (HunterEntity.Kind k : HunterEntity.Kind.values()) {
            String id = k.name().toLowerCase();
            HUNTERS.put(k, ENTITIES.register(id, () -> EntityType.Builder.<HunterEntity>of((t, l) -> new HunterEntity(t, l, k), MobCategory.MONSTER)
                    .sized(0.7f, k == HunterEntity.Kind.PREDATOR ? 2.5f : 2.8f).clientTrackingRange(10).build(id)));
            EGGS.add(ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(HUNTERS.get(k), k == HunterEntity.Kind.PREDATOR ? 0x6b7a3a : 0xd8d8d0, k == HunterEntity.Kind.PREDATOR ? 0x2a2e30 : 0x8a8f9a, new Item.Properties())));
        }
        for (int v = 1; v <= 2; v++) {
            int vv = v;
            EGGS.add(ITEMS.register((v == 1 ? "red_facehugger" : "royal_facehugger") + "_spawn_egg", () -> new Item(new Item.Properties()) {
                @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
                    if (c.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                        FacehuggerEntity f = FACEHUGGER.get().create(sl); net.minecraft.core.BlockPos p = c.getClickedPos().relative(c.getClickedFace());
                        f.setVariant(vv); f.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0); sl.addFreshEntity(f);
                        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
                    }
                    return net.minecraft.world.InteractionResult.sidedSuccess(c.getLevel().isClientSide);
                }
            }));
        }
        EGGS.add(ITEMS.register("red_egg_spawn_egg", () -> new Item(new Item.Properties()) {
            @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
                if (c.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                    XenoEggEntity e = EGG.get().create(sl); net.minecraft.core.BlockPos p = c.getClickedPos().relative(c.getClickedFace());
                    e.setRed(true); e.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0); sl.addFreshEntity(e);
                    if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(c.getLevel().isClientSide);
            }
        }));
        EGGS.add(ITEMS.register("facehugger_spawn_egg", () -> new ForgeSpawnEggItem(FACEHUGGER, 0xb59c7a, 0x6a5a40, new Item.Properties())));
        EGGS.add(ITEMS.register("chestburster_spawn_egg", () -> new ForgeSpawnEggItem(BURSTER, 0xd9a9a0, 0x7a2a2a, new Item.Properties())));
        EGGS.add(ITEMS.register("egg_spawn_egg", () -> new Item(new Item.Properties()) {
            @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
                if (c.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                    XenoEggEntity e = EGG.get().create(sl);
                    net.minecraft.core.BlockPos p = c.getClickedPos().relative(c.getClickedFace());
                    e.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0);
                    sl.addFreshEntity(e);
                    if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(c.getLevel().isClientSide);
            }
        }));
        TABS.register("xenomorph", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.xenomorph"))
                .icon(() -> new ItemStack(EGGS.get(0).get()))
                .displayItems((p, out) -> { EGGS.forEach(e -> out.accept(e.get())); out.accept(RESIN_ITEM.get()); }).build());
    }

    public XenomorphMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener((net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) -> HUNTERS.values().forEach(t -> e.register(t.get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (ty, lv, rs, ps, rn) -> net.minecraft.world.entity.Mob.checkMobSpawnRules(ty, lv, rs, ps, rn), net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.OR)));
        BLOCKS.register(bus); ENTITIES.register(bus); ITEMS.register(bus); EFFECTS.register(bus); TABS.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> {
            XENOS.forEach((c, t) -> e.put(t.get(), XenoEntity.attributes(c).build()));
            HUNTERS.forEach((k, t) -> e.put(t.get(), HunterEntity.attributes(k).build()));
            e.put(FACEHUGGER.get(), FacehuggerEntity.attributes().build());
            e.put(BURSTER.get(), ChestbursterEntity.attributes().build());
        });
    }
}
