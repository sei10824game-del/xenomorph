package com.xenomorph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenomorph.entity.ChestbursterEntity;
import com.xenomorph.entity.FacehuggerEntity;
import com.xenomorph.entity.XenoEggEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

import static com.xenomorph.client.Anim.*;

/** Facehugger, chestburster and egg models. */
final class SmallModels {
    private SmallModels() {}

    // ============================================================ FACEHUGGER
    static class Hugger extends EntityModel<FacehuggerEntity> {
        final ModelPart root, body, sac;
        final ModelPart[][] leg = new ModelPart[8][3];
        final ModelPart[] tail = new ModelPart[6];
        Hugger(ModelPart r) {
            root = r; body = r.getChild("body"); sac = body.getChild("sac");
            for (int i = 0; i < 8; i++) { ModelPart p = body.getChild("leg" + i); leg[i][0] = p; leg[i][1] = p.getChild("m"); leg[i][2] = leg[i][1].getChild("t"); }
            ModelPart p = body.getChild("tail0"); tail[0] = p;
            for (int i = 1; i < 6; i++) { p = p.getChild("tail" + i); tail[i] = p; }
        }
        static LayerDefinition create() {
            MeshDefinition m = new MeshDefinition(); PartDefinition r = m.getRoot();
            PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -4, 6, 3, 8).texOffs(0, 12).addBox(-2.5f, -3, -3, 5, 1, 6), PartPose.offset(0, 20, 0));
            body.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(0, 20).addBox(-2, -2, 0, 4, 3, 5), PartPose.offset(0, 0, 3.5f));
            for (int i = 0; i < 8; i++) {
                int side = i < 4 ? 1 : -1; int k = i % 4;
                float z = -3 + k * 2.1f;
                PartDefinition l = body.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(30, 0).addBox(-0.5f, -0.5f, -0.5f, 5, 1, 1), PartPose.offset(side * 2.5f, -0.5f, z));
                PartDefinition mid = l.addOrReplaceChild("m", CubeListBuilder.create().texOffs(30, 3).addBox(-0.5f, -0.5f, -0.5f, 6, 1, 1), PartPose.offset(4.5f, 0, 0));
                mid.addOrReplaceChild("t", CubeListBuilder.create().texOffs(30, 6).addBox(-0.4f, -0.4f, -0.4f, 5, 0.8f, 0.8f), PartPose.offset(5.5f, 0, 0));
            }
            PartDefinition t = body.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(0, 30).addBox(-1.2f, -1.2f, 0, 2.4f, 2.4f, 5), PartPose.offset(0, 0, 5));
            for (int i = 1; i < 6; i++) t = t.addOrReplaceChild("tail" + i, CubeListBuilder.create().texOffs(0, 30).addBox(-1f + i * 0.1f, -1f + i * 0.1f, 0, 2f - i * 0.2f, 2f - i * 0.2f, 5), PartPose.offset(0, 0, 5));
            return LayerDefinition.create(m, 64, 64);
        }
        @Override public void setupAnim(FacehuggerEntity e, float ls, float amt, float age, float yaw, float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            float pt = Minecraft.getInstance().getFrameTime();
            boolean att = e.isAttached();
            int lt = e.leapTick();
            float leapT = lt > 0 ? lt + pt : 0;
            float ph = ls * 1.7f;
            float spd = Mth.clamp(amt * 2, 0, 1);
            float air = lt > 0 ? smooth(leapT / 3f) : 0;
            float wrap = att ? 1 : 0;
            float pulse = Mth.sin(age * 0.18f);
            sac.zScale = 1 + 0.12f * pulse; sac.yScale = 1 + 0.2f * pulse * (att ? 2.5f : 1);
            for (int i = 0; i < 8; i++) {
                int side = i < 4 ? 1 : -1; int k = i % 4;
                float cyc = ph + k * 0.9f + (i < 4 ? 0 : Mth.PI) + (k % 2) * Mth.PI;
                float sw = Mth.sin(cyc) * 0.5f * spd, lf = Math.max(0, Mth.cos(cyc)) * 0.6f * spd;
                // rest: legs splay out and down, finger-like; idle twitch
                float idle = Mth.sin(age * 0.12f + i) * 0.06f;
                float spread = Mth.lerp(air, 0.0f, -0.55f);          // legs extend wide in flight
                float zr = side * (0.35f + lf * 0.6f + idle) ;
                // wrap: legs clamp inward and down (hugging a head)
                zr = Mth.lerp(wrap, zr, side * 1.45f);
                leg[i][0].zRot = -zr + (side == 1 ? 0 : 0);
                leg[i][0].zRot = side == 1 ? -zr : zr;
                leg[i][0].yRot = (side * (sw + (k - 1.5f) * 0.28f)) + spread * 0.0f + Mth.lerp(air, 0, side * (k - 1.5f) * 0.25f);
                leg[i][1].zRot = side == 1 ? -(0.7f + idle + wrap * 0.9f - air * 0.5f) : (0.7f + idle + wrap * 0.9f - air * 0.5f);
                leg[i][2].zRot = side == 1 ? -(0.6f + wrap * 0.8f - air * 0.4f) : (0.6f + wrap * 0.8f - air * 0.4f);
            }
            for (int i = 0; i < 6; i++) {
                float whip = Mth.sin(age * 0.2f - i * 0.8f) * 0.15f * (0.4f + spd);
                tail[i].yRot = whip + Mth.lerp(wrap, 0, 0.35f);
                tail[i].xRot = -0.08f * i + Mth.lerp(air, 0, 0.1f) + wrap * -0.55f * (i > 0 ? 1 : 0.2f);
            }
            body.xRot = Mth.lerp(air, 0, -0.4f) + (att ? 0.1f : 0);
            body.y = 20 - Mth.abs(Mth.sin(ph)) * 0.8f * spd + wrap * 0f;
        }
        @Override public void renderToBuffer(PoseStack ps, VertexConsumer vc, int l, int o, float r, float g, float b, float a) { root.render(ps, vc, l, o, r, g, b, a); }
    }

    // ============================================================ CHESTBURSTER
    static class Burster extends EntityModel<ChestbursterEntity> {
        static final int SEG = 9;
        final ModelPart root, head, jaw, inner;
        final ModelPart[] seg = new ModelPart[SEG];
        final ModelPart armL, armR;
        Burster(ModelPart r) {
            root = r; ModelPart p = r.getChild("seg0"); seg[0] = p;
            for (int i = 1; i < SEG; i++) { p = p.getChild("seg" + i); seg[i] = p; }
            head = r.getChild("seg0").getChild("head"); jaw = head.getChild("jaw"); inner = head.getChild("inner");
            armL = r.getChild("seg0").getChild("armL"); armR = r.getChild("seg0").getChild("armR");
        }
        static LayerDefinition create() {
            MeshDefinition m = new MeshDefinition(); PartDefinition r = m.getRoot();
            PartDefinition p = r.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.2f, -2.2f, -3, 4.4f, 4.4f, 6), PartPose.offset(0, 21.5f, -4));
            p.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 12).addBox(-2, -2.5f, -6, 4, 4, 6).texOffs(20, 12).addBox(-1.5f, -3.5f, -4, 3, 1.5f, 8), PartPose.offset(0, 0, -3))
                    .addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 24).addBox(-1.6f, 0, -5.5f, 3.2f, 1, 6).texOffs(0, 60).addBox(-1.4f, -0.8f, -5, 0.5f, 0.8f, 0.5f).addBox(0.9f, -0.8f, -5, 0.5f, 0.8f, 0.5f), PartPose.offset(0, 0.8f, 0));
            p.getChild("head").addOrReplaceChild("inner", CubeListBuilder.create().texOffs(20, 24).addBox(-0.7f, -0.5f, -3, 1.4f, 1, 3).texOffs(0, 60).addBox(-0.4f, -0.4f, -3.6f, 0.8f, 0.8f, 0.8f), PartPose.offset(0, 0.2f, -5));
            p.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5f, 0, -0.5f, 1, 3.5f, 1), PartPose.offset(2.3f, 0, -1));
            p.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5f, 0, -0.5f, 1, 3.5f, 1), PartPose.offset(-2.3f, 0, -1));
            PartDefinition q = p;
            for (int i = 1; i < SEG; i++) {
                float w = 4.2f - i * 0.4f;
                q = q.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(0, 32).addBox(-w / 2, -w / 2, 0, w, w, 4.6f), PartPose.offset(0, 0, i == 1 ? 3 : 4.6f));
            }
            return LayerDefinition.create(m, 64, 64);
        }
        @Override public void setupAnim(ChestbursterEntity e, float ls, float amt, float age, float yaw, float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            float pt = Minecraft.getInstance().getFrameTime();
            float a = e.age() + pt;
            float spd = Mth.clamp(amt * 2.2f, 0, 1);
            float ph = ls * 1.3f;
            float burst = a < ChestbursterEntity_BURST ? a / ChestbursterEntity_BURST : 1;
            float emerge = smooth(burst * 1.4f);                       // segments pop out progressively
            float screech = kf(a, 0, 0, 6, 1, 28, 1, 36, 0);
            for (int i = 0; i < SEG; i++) {
                seg[i].yRot = Mth.sin(ph - i * 0.75f) * 0.38f * spd + Mth.sin(age * 0.12f - i * 0.6f) * 0.06f;
                seg[i].xRot = Mth.sin(ph * 0.0f + age * 0.1f - i * 0.5f) * 0.03f;
                float vis = Mth.clamp(emerge * (SEG + 2) - i, 0, 1);
                seg[i].xScale = seg[i].yScale = seg[i].zScale = Math.max(0.01f, vis);
            }
            // rear up and screech when born
            seg[0].xRot = -0.65f * screech + 0.0f;
            seg[1].xRot = -0.3f * screech; seg[2].xRot = 0.2f * screech;
            head.xRot = -0.55f * screech - Mth.clamp(pitch, -30, 30) * Mth.DEG_TO_RAD * 0.5f;
            head.yRot = Mth.clamp(yaw, -50, 50) * Mth.DEG_TO_RAD * 0.5f + Mth.sin(a * 2.6f) * 0.1f * screech;
            jaw.xRot = 0.12f + 0.95f * screech + Mth.sin(age * 0.3f) * 0.04f;
            inner.z = -5 - 4.5f * screech * (0.6f + 0.4f * Mth.sin(a * 0.9f)) - Math.max(0, Mth.sin(age * 0.05f) - 0.9f) * 12f;
            float claw = Mth.sin(ph * 1.0f) * 0.5f * spd;
            armL.xRot = -0.4f + claw - 0.8f * screech; armR.xRot = -0.4f - claw - 0.8f * screech;
            armL.zRot = -0.6f; armR.zRot = 0.6f;
            seg[0].y = 21.5f - 3f * screech * 0.0f;
        }
        static final int ChestbursterEntity_BURST = ChestbursterEntity.BURST;
        @Override public void renderToBuffer(PoseStack ps, VertexConsumer vc, int l, int o, float r, float g, float b, float a) { root.render(ps, vc, l, o, r, g, b, a); }
    }

    // ============================================================ EGG
    static class Egg extends EntityModel<XenoEggEntity> {
        final ModelPart root, hugger;
        final ModelPart[] petal = new ModelPart[4];
        Egg(ModelPart r) {
            root = r; hugger = r.getChild("hugger");
            for (int i = 0; i < 4; i++) petal[i] = r.getChild("petal" + i);
        }
        static LayerDefinition create() {
            MeshDefinition m = new MeshDefinition(); PartDefinition r = m.getRoot();
            r.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -3, -6, 12, 3, 12).texOffs(0, 16).addBox(-7.5f, -1, -7.5f, 15, 1, 15).texOffs(0, 34).addBox(-5, -12, -5, 10, 9, 10), PartPose.offset(0, 24, 0));
            r.addOrReplaceChild("hugger", CubeListBuilder.create().texOffs(40, 0).addBox(-2.5f, -1.5f, -3, 5, 2, 6), PartPose.offset(0, 17, 0));
            // four petals hinged at the rim; closed they form the tip, open they fold outward
            for (int i = 0; i < 4; i++) {
                float ang = i * Mth.HALF_PI;
                r.addOrReplaceChild("petal" + i, CubeListBuilder.create().texOffs(0, 54).addBox(-4.5f, -11, -1.2f, 9, 11, 2.4f).texOffs(40, 54).addBox(-2.5f, -14.5f, -0.8f, 5, 4, 1.6f),
                        PartPose.offsetAndRotation(Mth.sin(ang) * 4.2f, 12, Mth.cos(ang) * 4.2f, 0, ang, 0));
            }
            return LayerDefinition.create(m, 128, 128);
        }
        @Override public void setupAnim(XenoEggEntity e, float ls, float amt, float age, float yaw, float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            float pt = Minecraft.getInstance().getFrameTime();
            float open = Mth.lerp(pt, e.openO, e.open()) / 40f;
            float throb = Mth.sin(age * 0.07f) * 0.01f;
            for (int i = 0; i < 4; i++) {
                // petals tilt about their hinge: closed = leaning inward over the egg, open = splayed out
                float ang = i * Mth.HALF_PI;
                petal[i].xRot = Mth.lerp(smooth(open), 0.18f, -1.25f) + Mth.sin(age * 0.2f + i) * 0.015f * (1 + open * 3);
                petal[i].zRot = 0;
                petal[i].x = Mth.sin(ang) * 4.2f; petal[i].z = Mth.cos(ang) * 4.2f;
            }
            hugger.visible = open > 0.15f;
            hugger.y = 17 - 2 * Mth.sin(age * 0.3f) * open;
            hugger.yRot = age * 0.05f;
            root.xScale = root.zScale = 1 + throb; root.yScale = 1 - throb;
        }
        @Override public void renderToBuffer(PoseStack ps, VertexConsumer vc, int l, int o, float r, float g, float b, float a) { root.render(ps, vc, l, o, r, g, b, a); }
    }
}
