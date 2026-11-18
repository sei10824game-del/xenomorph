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
    public static final RegistryObject<net.minecraft.world.level.block.Block> RESIN = BLOCKS.register("resin", () -> new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN).strength(3f, 6f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.HONEY_BLOCK)) {
        /** Hive ambience: wet drips, a low distant hiss, faint metallic clinks. */
        @Override public void animateTick(net.minecraft.world.level.block.state.BlockState st, net.minecraft.world.level.Level lv, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource r) {
            if (r.nextInt(90) != 0) return;
            double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
            switch (r.nextInt(4)) {
                case 0, 1 -> { lv.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, net.minecraft.sounds.SoundSource.AMBIENT, 0.35f, 0.7f + r.nextFloat() * 0.3f, false); lv.addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, x, y - 0.5, z, 0, 0, 0); }
                case 2 -> lv.playLocalSound(x, y, z, HISS.get(), net.minecraft.sounds.SoundSource.AMBIENT, 0.25f, 0.4f + r.nextFloat() * 0.15f, false);
                default -> lv.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.CHAIN_STEP, net.minecraft.sounds.SoundSource.AMBIENT, 0.3f, 0.5f + r.nextFloat() * 0.2f, false);
            }
        }
    });
    static final RegistryObject<Item> RIFLE = ITEMS.register("pulse_rifle", XenoWeapons.PulseRifle::new), FLAMER = ITEMS.register("flamethrower", XenoWeapons.Flamethrower::new),
            GLAND = ITEMS.register("acid_gland", () -> new Item(new Item.Properties())),
            SPEAR = ITEMS.register("acid_spear", XenoWeapons.AcidSpear::new), PEDIA = ITEMS.register("xenopedia", Xenopedia::new), MINION = ITEMS.register("minion_egg", MinionEgg::new), HAMMER = ITEMS.register("hulk_hammer", MutantGear.HulkHammer::new), CLAW = ITEMS.register("void_claw", MutantGear.VoidClaw::new), SKIN_ = ITEMS.register("shed_skin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SKIN = SKIN_;
    static final List<RegistryObject<Item>> SUIT = new ArrayList<>();
    static {
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        net.minecraft.world.item.ArmorItem.Type[] types = {net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS};
        for (int i = 0; i < 4; i++) { var ty = types[i]; SUIT.add(ITEMS.register("acidproof_" + names[i], () -> new net.minecraft.world.item.ArmorItem(XenoArmor.MATERIAL, ty, new Item.Properties()))); }
    }
    public static final RegistryObject<Item> FRAGMENT = ITEMS.register("bone_fragment", () -> new Item(new Item.Properties()));
    static final List<RegistryObject<Item>> BONE_SET = new ArrayList<>();
    static {
        String[] nm = {"helmet", "chestplate", "leggings", "boots"};
        net.minecraft.world.item.ArmorItem.Type[] ty = {net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS};
        for (int i = 0; i < 4; i++) { var t = ty[i]; BONE_SET.add(ITEMS.register("bone_" + nm[i], () -> new net.minecraft.world.item.ArmorItem(XenoArmor.BONE, t, new Item.Properties()))); }
    }
    static final RegistryObject<Item> KIT = ITEMS.register("surgical_kit", SurgicalKit::new);
    static final RegistryObject<Item> LOCATOR = ITEMS.register("hive_locator", HiveLocator::new);
    static final RegistryObject<Item> JELLY = ITEMS.register("royal_jelly", () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(0.9f).alwaysEat()
            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1), 1f).effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200, 0), 1f).build())));
    static final RegistryObject<Item> TRACKER = ITEMS.register("motion_tracker", MotionTracker::new);
    static final RegistryObject<Item> RESIN_ITEM = ITEMS.register("resin", () -> new net.minecraft.world.item.BlockItem(RESIN.get(), new Item.Properties()));
    static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static RegistryObject<net.minecraft.sounds.SoundEvent> snd(String n) { return SOUNDS.register(n, () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(ID, n))); }
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> HISS = snd("xeno.hiss"), SCREECH = snd("xeno.screech"), HURT = snd("xeno.hurt"), DEATH = snd("xeno.death"),
            ATTACK = snd("xeno.attack"), SKITTER = snd("xeno.skitter"), SIZZLE = snd("xeno.sizzle"), BOSS_THEME = snd("xeno.boss_theme");
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
    public static final RegistryObject<MobEffect> EMBRYO = EFFECTS.register("embryo", () -> new EmbryoEffect(false, false));
    public static final RegistryObject<MobEffect> PRAETO_EMBRYO = EFFECTS.register("praeto_embryo", () -> new EmbryoEffect(false, true));
    public static final RegistryObject<MobEffect> RED_EMBRYO = EFFECTS.register("red_embryo", () -> new EmbryoEffect(true, false));
    static final List<RegistryObject<Item>> EGGS = new ArrayList<>();

    static {
        for (Caste c : Caste.values()) {
            XENOS.put(c, ENTITIES.register(c.id, () -> (c.fireproof() ? EntityType.Builder.<XenoEntity>of((t, l) -> new XenoEntity(t, l, c), MobCategory.MONSTER).fireImmune() : EntityType.Builder.<XenoEntity>of((t, l) -> new XenoEntity(t, l, c), MobCategory.MONSTER))
                    .sized(0.8f * c.scale, 2.1f * c.scale).clientTrackingRange(10).build(c.id)));
            EGGS.add(ITEMS.register(c.id + "_spawn_egg", () -> new ForgeSpawnEggItem(XENOS.get(c), 0x14181c, 0x3a5a4a + c.ordinal() * 0x050301, new Item.Properties())));
        }
        for (HunterEntity.Kind k : HunterEntity.Kind.values()) {
            String id = k.name().toLowerCase();
            HUNTERS.put(k, ENTITIES.register(id, () -> EntityType.Builder.<HunterEntity>of((t, l) -> new HunterEntity(t, l, k), MobCategory.MONSTER)
                    .sized(0.7f, k == HunterEntity.Kind.PREDATOR ? 2.5f : 2.8f).clientTrackingRange(10).build(id)));
            EGGS.add(ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(HUNTERS.get(k), k == HunterEntity.Kind.PREDATOR ? 0x6b7a3a : 0xd8d8d0, k == HunterEntity.Kind.PREDATOR ? 0x2a2e30 : 0x8a8f9a, new Item.Properties())));
        }
        for (int v = 1; v <= 3; v++) {
            int vv = v;
            EGGS.add(ITEMS.register((v == 1 ? "red_facehugger" : v == 2 ? "royal_facehugger" : "praeto_facehugger") + "_spawn_egg", () -> new Item(new Item.Properties()) {
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
                .displayItems((p, out) -> { EGGS.forEach(e -> out.accept(e.get())); out.accept(RESIN_ITEM.get()); out.accept(TRACKER.get()); out.accept(RIFLE.get()); out.accept(FLAMER.get()); out.accept(GLAND.get()); out.accept(SPEAR.get()); out.accept(SKIN_.get()); out.accept(PEDIA.get()); out.accept(MINION.get()); out.accept(HAMMER.get()); out.accept(CLAW.get()); out.accept(KIT.get()); out.accept(LOCATOR.get()); out.accept(JELLY.get()); SUIT.forEach(a -> out.accept(a.get())); out.accept(FRAGMENT.get()); BONE_SET.forEach(a -> out.accept(a.get())); }).build());
    }

    public XenomorphMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, XenoConfig.SPEC);
        bus.addListener((net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) -> { for (Caste c : Caste.values()) if (c.wild()) e.register(XENOS.get(c).get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND, net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (ty, lv, rs, ps, rn) -> net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(ty, lv, rs, ps, rn), net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.OR); });
        bus.addListener((net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) -> HUNTERS.values().forEach(t -> e.register(t.get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (ty, lv, rs, ps, rn) -> net.minecraft.world.entity.Mob.checkMobSpawnRules(ty, lv, rs, ps, rn), net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.OR)));
        SOUNDS.register(bus); BLOCKS.register(bus); ENTITIES.register(bus); ITEMS.register(bus); EFFECTS.register(bus); TABS.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> {
            XENOS.forEach((c, t) -> e.put(t.get(), XenoEntity.attributes(c).build()));
            HUNTERS.forEach((k, t) -> e.put(t.get(), HunterEntity.attributes(k).build()));
            e.put(FACEHUGGER.get(), FacehuggerEntity.attributes().build());
            e.put(BURSTER.get(), ChestbursterEntity.attributes().build());
        });
    }
}
