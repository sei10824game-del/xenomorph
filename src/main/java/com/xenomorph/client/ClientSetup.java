package com.xenomorph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenomorph.XenomorphMod;
import com.xenomorph.entity.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenomorphMod.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    static ModelLayerLocation hunterLayer(HunterEntity.Kind k) { return new ModelLayerLocation(new ResourceLocation(XenomorphMod.ID, k.name().toLowerCase()), "main"); }
    static ModelLayerLocation layer(Caste c) { return new ModelLayerLocation(new ResourceLocation(XenomorphMod.ID, c.id), "main"); }
    static final ModelLayerLocation HUGGER = new ModelLayerLocation(new ResourceLocation(XenomorphMod.ID, "facehugger"), "main");
    static final ModelLayerLocation BURSTER = new ModelLayerLocation(new ResourceLocation(XenomorphMod.ID, "chestburster"), "main");
    static final ModelLayerLocation EGG = new ModelLayerLocation(new ResourceLocation(XenomorphMod.ID, "egg"), "main");

    private static ResourceLocation tex(String n) { return new ResourceLocation(XenomorphMod.ID, "textures/entity/" + n + ".png"); }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (Caste c : Caste.values()) e.registerLayerDefinition(layer(c), () -> XenoModel.create(c));
        for (HunterEntity.Kind k : HunterEntity.Kind.values()) e.registerLayerDefinition(hunterLayer(k), () -> HunterModel.create(k));
        e.registerLayerDefinition(HUGGER, SmallModels.Hugger::create);
        e.registerLayerDefinition(BURSTER, SmallModels.Burster::create);
        e.registerLayerDefinition(EGG, SmallModels.Egg::create);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        XenomorphMod.XENOS.forEach((c, t) -> e.registerEntityRenderer(t.get(), ctx -> new XenoRenderer(ctx, c)));
        e.registerEntityRenderer(XenomorphMod.FACEHUGGER.get(), ctx -> new MobRenderer<FacehuggerEntity, SmallModels.Hugger>(ctx, new SmallModels.Hugger(ctx.bakeLayer(HUGGER)), 0.3f) {
            @Override public ResourceLocation getTextureLocation(FacehuggerEntity x) { return tex("facehugger"); }
            @Override protected void setupRotations(FacehuggerEntity x, PoseStack ps, float a, float b, float pt) { super.setupRotations(x, ps, a, b, pt); }
        });
        e.registerEntityRenderer(XenomorphMod.BURSTER.get(), ctx -> new MobRenderer<ChestbursterEntity, SmallModels.Burster>(ctx, new SmallModels.Burster(ctx.bakeLayer(BURSTER)), 0.25f) {
            @Override public ResourceLocation getTextureLocation(ChestbursterEntity x) { return tex("chestburster"); }
        });
        XenomorphMod.HUNTERS.forEach((k, t) -> e.registerEntityRenderer(t.get(), ctx -> new net.minecraft.client.renderer.entity.HumanoidMobRenderer<HunterEntity, HunterModel>(ctx, new HunterModel(ctx.bakeLayer(hunterLayer(k))), 0.6f) {
            @Override public ResourceLocation getTextureLocation(HunterEntity x) { return tex(k.name().toLowerCase()); }
            @Override protected void scale(HunterEntity x, PoseStack ps, float pt) { float s = k == HunterEntity.Kind.PREDATOR ? 1.25f : 1.4f; ps.scale(s, s, s); }
        }));
        e.registerEntityRenderer(XenomorphMod.EGG.get(), EggRenderer::new);
        e.registerEntityRenderer(XenomorphMod.SPIT.get(), NoopRenderer::new);
    }

    static class XenoRenderer extends MobRenderer<XenoEntity, XenoModel> {
        private final ResourceLocation texture;
        XenoRenderer(EntityRendererProvider.Context ctx, Caste c) {
            super(ctx, new XenoModel(ctx.bakeLayer(layer(c))), 0.6f * c.scale);
            texture = tex(c.id);
        }
        @Override public ResourceLocation getTextureLocation(XenoEntity e) { return texture; }
        @Override protected void scale(XenoEntity e, PoseStack ps, float pt) { float s = e.caste.scale; ps.scale(s, s, s); }
        @Override protected float getFlipDegrees(XenoEntity e) { return 90f; }
    }

    static class EggRenderer extends EntityRenderer<XenoEggEntity> {
        private final SmallModels.Egg model;
        EggRenderer(EntityRendererProvider.Context ctx) { super(ctx); model = new SmallModels.Egg(ctx.bakeLayer(EGG)); shadowRadius = 0.5f; }
        @Override public ResourceLocation getTextureLocation(XenoEggEntity e) { return tex("egg"); }
        @Override public void render(XenoEggEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            ps.scale(-1, -1, 1); ps.translate(0, -1.501, 0);
            model.setupAnim(e, 0, 0, e.tickCount + pt, 0, 0);
            model.renderToBuffer(ps, buf.getBuffer(model.renderType(getTextureLocation(e))), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }
    }
}
