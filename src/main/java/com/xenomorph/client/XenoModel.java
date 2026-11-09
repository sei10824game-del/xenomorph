package com.xenomorph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenomorph.entity.Attack;
import com.xenomorph.entity.Caste;
import com.xenomorph.entity.XenoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

import static com.xenomorph.client.Anim.*;

/** Fully procedural xenomorph: 2-bone IK gait, quad/biped blend, per-attack keyframed poses. */
public class XenoModel extends EntityModel<XenoEntity> {
    static final int TAIL = 8;
    final ModelPart root, hips, body, chest, neck, head, jaw, inner, dome, ridges, fan, horns, dreads, bulb, tubeL, tubeR, tail0;
    final ModelPart[] tail = new ModelPart[TAIL];
    final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
    final ModelPart[] arm2 = new ModelPart[2], fore2 = new ModelPart[2];
    final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];

    public XenoModel(ModelPart r) {
        root = r; hips = r.getChild("hips");
        body = hips.getChild("body"); chest = body.getChild("chest"); neck = chest.getChild("neck"); head = neck.getChild("head");
        jaw = head.getChild("jaw"); inner = head.getChild("inner"); dome = head.getChild("dome"); ridges = head.getChild("ridges");
        fan = head.getChild("fan"); horns = head.getChild("horns"); dreads = head.getChild("dreads"); bulb = head.getChild("bulb");
        tubeL = chest.getChild("tubeL"); tubeR = chest.getChild("tubeR");
        for (int s = 0; s < 2; s++) {
            String n = s == 0 ? "L" : "R";
            arm[s] = chest.getChild("arm" + n); fore[s] = arm[s].getChild("fore"); hand[s] = fore[s].getChild("hand");
            arm2[s] = chest.getChild("arm2" + n); fore2[s] = arm2[s].getChild("fore");
            thigh[s] = hips.getChild("thigh" + n); shin[s] = thigh[s].getChild("shin"); foot[s] = shin[s].getChild("foot");
        }
        tail0 = hips.getChild("tail0");
        ModelPart p = tail0;
        for (int i = 0; i < TAIL; i++) { tail[i] = i == 0 ? tail0 : p.getChild("tail" + i); p = tail[i]; }
    }

    // ------------------------------------------------------------------ geometry
    private static int[] uv;
    private static int cu, cv, rowH;
    private static CubeListBuilder box(float x, float y, float z, float w, float h, float d) {
        int bw = (int) Math.ceil(2 * (w + d)) + 1, bh = (int) Math.ceil(h + d) + 1;
        if (cu + bw > 256) { cu = 0; cv += rowH; rowH = 0; }
        if (cv + bh > 96) { cu = 0; cv = 0; rowH = 0; }
        CubeListBuilder b = CubeListBuilder.create().texOffs(cu, cv).addBox(x, y, z, w, h, d);
        cu += bw; rowH = Math.max(rowH, bh);
        return b;
    }
    private static CubeListBuilder add(CubeListBuilder b, float x, float y, float z, float w, float h, float d) {
        int bw = (int) Math.ceil(2 * (w + d)) + 1, bh = (int) Math.ceil(h + d) + 1;
        if (cu + bw > 256) { cu = 0; cv += rowH; rowH = 0; }
        if (cv + bh > 96) { cu = 0; cv = 0; rowH = 0; }
        b.texOffs(cu, cv).addBox(x, y, z, w, h, d);
        cu += bw; rowH = Math.max(rowH, bh);
        return b;
    }
    private static CubeListBuilder tooth(CubeListBuilder b, float x, float y, float z, float w, float h, float d) {
        return b.texOffs(0, 112).addBox(x, y, z, w, h, d);
    }
    private static CubeListBuilder pipe(float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(0, 96).addBox(x, y, z, w, h, d);
    }

    public static LayerDefinition create() {
        cu = cv = rowH = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", add(box(-3.5f, -3f, -4f, 7, 6, 9), -2.5f, -4, -1, 5, 1.5f, 5), PartPose.offset(0, 11, 1));

        // torso: segmented, ribbed
        CubeListBuilder bodyB = box(-3, -9, -3, 6, 10, 6);
        add(bodyB, -1, -9, 2.8f, 2, 9, 1.5f);                     // dorsal ridge
        PartDefinition body = hips.addOrReplaceChild("body", bodyB, PartPose.offset(0, -1, -1));
        CubeListBuilder chestB = box(-3.8f, -8.5f, -3.8f, 7.6f, 9, 7.6f);
        for (int i = 0; i < 3; i++) { add(chestB, -4.3f, -7 + i * 2.6f, -3, 0.8f, 1.2f, 6); add(chestB, 3.5f, -7 + i * 2.6f, -3, 0.8f, 1.2f, 6); }
        add(chestB, -1, -8.5f, 3.7f, 2, 8, 1.5f);
        PartDefinition chest = body.addOrReplaceChild("chest", chestB, PartPose.offset(0, -8.5f, 0));
        PartDefinition neck = chest.addOrReplaceChild("neck", box(-2, -5, -2, 4, 6, 4), PartPose.offset(0, -7.5f, 0));

        // head
        PartDefinition head = neck.addOrReplaceChild("head", add(box(-2.4f, -3.5f, -6, 4.8f, 4.5f, 6), -1.5f, -4.5f, -4, 3, 1, 4), PartPose.offset(0, -4.5f, 0));
        head.addOrReplaceChild("dome", add(box(-2.2f, -5.5f, -1, 4.4f, 4.5f, 12), -1.4f, -6.2f, 2, 2.8f, 1, 8), PartPose.ZERO);
        CubeListBuilder rid = box(-0.7f, -7, 0, 1.4f, 1.4f, 3);
        add(rid, -0.7f, -6.8f, 4, 1.4f, 1.4f, 3); add(rid, -0.7f, -6.5f, 8, 1.4f, 1.4f, 3);
        head.addOrReplaceChild("ridges", rid, PartPose.ZERO);
        head.addOrReplaceChild("fan", add(box(-6, -11, 4.5f, 12, 9, 1), -4, -13, 4.5f, 8, 3, 1), PartPose.rotation(0.25f, 0, 0));
        CubeListBuilder hornB = box(-6.5f, -9, 0, 13, 9, 1.5f);   // crusher frill
        add(hornB, -7.5f, -3.5f, -3, 2, 2, 7); add(hornB, 5.5f, -3.5f, -3, 2, 2, 7);
        add(hornB, -1, -8, -6, 2, 3, 3);
        head.addOrReplaceChild("horns", hornB, PartPose.offsetAndRotation(0, -1, -1.5f, -0.1f, 0, 0));
        CubeListBuilder dr = box(-3, -1, 0, 1, 9, 1);
        add(dr, -1, -1, 0, 1, 11, 1); add(dr, 1, -1, 0, 1, 10, 1); add(dr, 3, -1, 0, 1, 8, 1);
        head.addOrReplaceChild("dreads", dr, PartPose.offsetAndRotation(0, -3.5f, 9, 0.5f, 0, 0));
        head.addOrReplaceChild("bulb", add(box(-3.2f, -7, -4, 6.4f, 7.5f, 7), -2, -3, -6, 4, 3, 2), PartPose.ZERO);

        CubeListBuilder jawB = box(-2, 0, -6, 4, 1.5f, 8);
        for (int i = 0; i < 4; i++) { tooth(jawB, -1.8f, -1, -5.5f + i * 1.6f, 0.5f, 1.1f, 0.5f); tooth(jawB, 1.3f, -1, -5.5f + i * 1.6f, 0.5f, 1.1f, 0.5f); }
        head.addOrReplaceChild("jaw", jawB, PartPose.offset(0, 1, 1));
        CubeListBuilder inB = box(-1, -0.7f, -4, 2, 1.4f, 4.5f);
        tooth(inB, -0.7f, -0.5f, -5.2f, 1.4f, 1.0f, 1.2f); tooth(inB, -0.5f, -1.2f, -4.8f, 1, 0.6f, 0.6f); tooth(inB, -0.5f, 0.6f, -4.6f, 1, 0.6f, 0.6f);
        head.addOrReplaceChild("inner", inB, PartPose.offset(0, 0.4f, -3.5f));
        CubeListBuilder upT = CubeListBuilder.create();
        for (int i = 0; i < 4; i++) { tooth(upT, -2, 0.9f, -5.7f + i * 1.5f, 0.5f, 1.2f, 0.5f); tooth(upT, 1.5f, 0.9f, -5.7f + i * 1.5f, 0.5f, 1.2f, 0.5f); }
        head.addOrReplaceChild("upperTeeth", upT, PartPose.ZERO);

        // back pipes
        chest.addOrReplaceChild("tubeL", pipe(-0.9f, -1, 0, 1.8f, 1.8f, 11), PartPose.offsetAndRotation(2, -6.5f, 3.6f, 0.25f, 0.1f, 0));
        chest.addOrReplaceChild("tubeR", pipe(-0.9f, -1, 0, 1.8f, 1.8f, 11), PartPose.offsetAndRotation(-2, -6.5f, 3.6f, 0.25f, -0.1f, 0));

        // arms (index 0 = left / +x)
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            PartDefinition a = chest.addOrReplaceChild("arm" + n, box(-1.6f, -1, -1.6f, 3.2f, 7, 3.2f), PartPose.offset(sx * 5, -6.5f, 0));
            PartDefinition f = a.addOrReplaceChild("fore", box(-1.3f, 0, -1.3f, 2.6f, 7.5f, 2.6f), PartPose.offset(0, 6, 0));
            CubeListBuilder h = box(-1.6f, 0, -2.5f, 3.2f, 1.6f, 4.5f);
            for (int k = -1; k <= 1; k++) add(h, k * 1.0f - 0.35f, 0.3f, -6.2f, 0.7f, 0.9f, 3.8f);
            f.addOrReplaceChild("hand", h, PartPose.offset(0, 7.5f, 0));
            PartDefinition a2 = chest.addOrReplaceChild("arm2" + n, box(-1, -1, -1, 2, 5, 2), PartPose.offset(sx * 3.6f, -3.5f, -2.5f));
            a2.addOrReplaceChild("fore", add(box(-0.8f, 0, -0.8f, 1.6f, 5, 1.6f), -1.2f, 5, -2.2f, 2.4f, 1, 3), PartPose.offset(0, 4.5f, 0));
        }
        // legs
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            PartDefinition t = hips.addOrReplaceChild("thigh" + n, box(-2.2f, -1, -2.3f, 4.4f, 9.5f, 4.6f), PartPose.offset(sx * 4.2f, 0, 1));
            PartDefinition sh = t.addOrReplaceChild("shin", box(-1.5f, -0.5f, -1.5f, 3, 8.5f, 3), PartPose.offset(0, 8, 0));
            CubeListBuilder ft = box(-1.8f, 0, -5, 3.6f, 1.6f, 6.5f);
            for (int k = -1; k <= 1; k++) add(ft, k * 1.1f - 0.4f, 0.4f, -7.5f, 0.8f, 1, 3);
            sh.addOrReplaceChild("foot", ft, PartPose.offset(0, 8, 0));
        }
        // tail
        PartDefinition tp = hips.addOrReplaceChild("tail0", box(-2.4f, -2.4f, 0, 4.8f, 4.8f, 7), PartPose.offset(0, -0.5f, 4.5f));
        for (int i = 1; i < TAIL; i++) {
            float w = 4.4f - i * 0.42f;
            CubeListBuilder cb = box(-w / 2, -w / 2, 0, w, w, 7);
            if (i == TAIL - 1) { add(cb, -0.4f, -3.4f, 3, 0.8f, 6.8f, 6); add(cb, -0.4f, -2.2f, 8, 0.8f, 4.4f, 4); }
            tp = tp.addOrReplaceChild("tail" + i, cb, PartPose.offset(0, 0, 7));
        }
        return LayerDefinition.create(mesh, 256, 128);
    }

    // ------------------------------------------------------------------ animation
    private float P;  // current chest pitch (world, rad)

    @Override
    public void setupAnim(XenoEntity e, float limbSwing, float amt, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        for (ModelPart p : root.getAllParts().toList()) { p.xScale = p.yScale = p.zScale = 1; }
        float pt = Minecraft.getInstance().getFrameTime();
        Caste c = e.caste;
        float b = smooth(Mth.lerp(pt, e.bipedAmtO, e.bipedAmt));
        Attack at = e.attack();
        float t = e.attackTick + pt;
        float aw = 0;                                   // attack weight 0..1
        if (at != Attack.NONE) aw = smooth(t / 3f) * smooth((at.duration - t) / 4f);

        // ---- visibility per caste
        dome.visible = c.crest != 6; ridges.visible = c.crest == 1 || c.crest == 4 || c.crest == 5; fan.visible = c.crest == 2;
        horns.visible = c.crest == 3; dreads.visible = c.dreads; bulb.visible = c.crest == 6;
        tubeL.visible = tubeR.visible = c.tubes; tail0.visible = c.crest != 6;
        arm2[0].visible = arm2[1].visible = c.extraArms;
        float domeLen = switch (c.crest) { case 3 -> 0.55f; case 7 -> 0.7f; case 4 -> 0.85f; case 2 -> 1.2f; default -> 1f; };
        dome.zScale = domeLen; dome.yScale = c.crest == 5 ? 1.2f : 1f;
        if (c.crest == 6) { jaw.zScale = 0.7f; head.yScale = 1.05f; }

        // ---- stance
        P = Mth.lerp(b, 1.38f, 0.5f);
        float H = Mth.lerp(b, 10.5f, 13.6f);
        float spd = Mth.clamp(amt * 1.6f, 0, 1);
        float ph = limbSwing * 0.82f;
        float crouch = 0;
        float bodyX = 0, bodyY = 0, chestX = 0, chestY = 0, chestZ = 0, neckX = 0, neckY = 0, headX = 0, headY = 0, headZ = 0;
        float jawOpen = 0, innerExt = 0, hipZ = 0, rootSpin = 0;
        float chestScale = 1, neckScale = 1;
        float[] tailYaw = new float[TAIL], tailPitch = new float[TAIL];
        float tailBaseY = 0, tailBaseX = 0;
        // arm overrides (world pitch etc.)
        float[][] A = new float[2][6];  // [pitch, yaw, roll, elbow, wrist, weight]
        float[] legOv = new float[]{0, 0, 0};  // blend, tz, ty
        float legSpeed = 1;

        float breathe = Mth.sin(age * 0.09f);
        float sway = Mth.sin(age * 0.045f);

        // ---------- idle / locomotion into base pose
        chestX += breathe * 0.025f; neckX += breathe * -0.02f + sway * 0.02f;
        neckY += sway * 0.12f * (1 - spd); headY += Mth.sin(age * 0.031f + 1) * 0.18f * (1 - spd);
        jawOpen += 0.06f + Mth.sin(age * 0.2f) * 0.03f * (1 - spd) + (Mth.sin(age * 0.013f) > 0.97f ? 0.4f : 0);
        innerExt += Math.max(0, Mth.sin(age * 0.07f + 2) - 0.8f) * 6f;      // occasional inner-jaw flick
        float bob = -Mth.abs(Mth.sin(ph)) * 0.9f * spd * (0.4f + b * 0.6f);
        // spine undulation
        float und = Mth.sin(ph) * 0.22f * spd * (1.2f - 0.6f * b);
        bodyY += und; chestY -= und * 1.3f; neckY += und * 0.6f; headY -= und * 0.5f;
        float walkRoll = Mth.sin(ph) * 0.05f * spd;
        // tail
        for (int i = 0; i < TAIL; i++) {
            float w = (0.14f + 0.12f * spd) * (0.6f + i * 0.14f);
            tailYaw[i] = Mth.sin(age * 0.05f - i * 0.55f + ph * 0.0f) * 0.08f * (0.6f + i * 0.1f) + Mth.sin(ph * 1.0f - i * 0.7f) * w * spd;
            tailPitch[i] = -0.035f * i + Mth.sin(ph * 2 - i * 0.6f) * 0.05f * spd;
        }
        tailBaseX = Mth.lerp(b, -0.15f, -0.55f);   // biped: tail arches higher for balance, quad: trails

        // arms loco
        for (int s = 0; s < 2; s++) {
            float sign = s == 0 ? 1 : -1;
            float swing = Mth.sin(ph + (s == 0 ? Mth.PI : 0));
            // biped: relaxed hang with ready claws, counter swing
            A[s][0] = -0.55f + swing * 0.55f * spd * b - breathe * 0.04f;
            A[s][1] = sign * (-0.25f - 0.1f * b);                 // splay outward (yaw)
            A[s][2] = 0;
            A[s][3] = 0.9f + 0.5f * (1 - spd) - swing * 0.25f * spd;
            A[s][4] = 0.3f;
            A[s][5] = 0;
        }

        // ---------- attack overlays (all times in ticks)
        if (at != Attack.NONE) {
            float hit = at.hit, dur = at.duration;
            switch (at) {
                case CLAW_L, CLAW_R -> {
                    int s = at == Attack.CLAW_L ? 0 : 1; float sg = s == 0 ? 1 : -1;
                    A[s][0] = kf(t, 0, -0.6f, 4.5f, -2.3f, 6.5f, -1.55f, 9f, -1.15f, 14f, -0.6f);
                    A[s][1] = sg * kf(t, 0, -0.2f, 4.5f, -1.15f, 6.5f, 0.2f, 9f, 1.0f, 14f, 0.3f);
                    A[s][2] = sg * kf(t, 0, 0, 4.5f, -0.5f, 8, 0.4f, 14, 0);
                    A[s][3] = kf(t, 0, 0.9f, 4.5f, 1.4f, 7f, 0.15f, 9, 0.35f, 14, 0.9f);
                    A[s][4] = kf(t, 0, .3f, 4.5f, -0.6f, 7, 0.7f, 14, .3f);
                    A[s][5] = aw;
                    int o = 1 - s;                                   // off hand braces / guard
                    A[o][0] = -1.2f; A[o][3] = 1.3f; A[o][1] = (o == 0 ? 1 : -1) * 0.2f; A[o][5] = aw * 0.7f;
                    chestY += sg * kf(t, 0, 0, 4.5f, -0.55f, 7f, 0.65f, 14, 0);
                    bodyY += sg * kf(t, 0, 0, 4.5f, -0.2f, 7f, 0.3f, 14, 0);
                    chestX += kf(t, 0, 0, 4.5f, -0.12f, 7f, 0.2f, 14, 0);
                    hipZ -= kf(t, 0, 0, 4.5f, 0.5f, 7, -2f, 14, 0);
                    headY += sg * kf(t, 0, 0, 4.5f, -0.2f, 7, 0.35f, 14, 0);
                    jawOpen += kf(t, 0, 0, 5, 0.3f, 7, 0.7f, 14, 0);
                    tailYaw[0] += -sg * kf(t, 0, 0, 5, 0.5f, 8, -0.4f, 14, 0);
                }
                case DOUBLE_CLAW -> {
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1; float d = s == 0 ? 0 : 6;
                        A[s][0] = kf(t, 0, -0.6f, 5, -2.5f, 7 + d, -2.2f + (d > 0 ? 0 : 0), 9 + d, -1.3f, 17 + d, -1.2f, 24, -0.6f);
                        A[s][0] = kf(t - d, 0, -0.6f, 5, -2.5f, 7, -2.4f, 9.5f, -1.3f, 16, -1.2f, 24 - d, -0.6f);
                        A[s][1] = sg * kf(t - d, 0, -0.2f, 5, -1.3f, 7, -1.2f, 9.5f, 1.05f, 16, 0.8f, 24 - d, 0.2f);
                        A[s][2] = sg * kf(t - d, 0, 0, 5, -0.6f, 9.5f, 0.5f, 24, 0);
                        A[s][3] = kf(t - d, 0, 0.9f, 5, 1.5f, 9.5f, 0.2f, 16, 0.4f, 24, 0.9f);
                        A[s][4] = kf(t - d, 0, 0.3f, 5, -0.7f, 9.5f, 0.8f, 24, 0.3f);
                        A[s][5] = aw;
                    }
                    chestX += kf(t, 0, 0, 6, -0.4f, 9, 0.35f, 16, 0.3f, 24, 0);
                    chestY += kf(t, 0, 0, 5, -0.4f, 9.5f, 0.45f, 12, 0.1f, 15, -0.45f, 20, 0.1f, 24, 0);
                    hipZ -= kf(t, 0, 0, 6, 1.5f, 9, -2.5f, 24, 0);
                    crouch += kf(t, 0, 0, 6, 1.2f, 9, -0.6f, 24, 0);
                    headX += kf(t, 0, 0, 6, -0.3f, 9, 0.5f, 24, 0);
                    jawOpen += kf(t, 0, 0, 6, 0.4f, 9, 0.9f, 24, 0);
                    innerExt += kf(t, 0, 0, 9, 0, 11, 3, 18, 0);
                    tailYaw[0] += kf(t, 0, 0, 7, 0.6f, 15, -0.6f, 24, 0);
                }
                case TAIL_STAB -> {
                    float coil = kf(t, 0, 0, 3, 0.04f, 8, 0.2f, 10, 0.16f, 11f, 0.04f, 15, 0.1f, 22, 0);
                    for (int i = 0; i < TAIL; i++) {
                        tailPitch[i] = -coil * (0.5f + i * 0.12f) * (1 + 0.6f * kf(t, 0, 0, 8, 0, 11, 1, 16, 0.4f, 22, 0)) - 0.02f;
                        tailYaw[i] = tailYaw[i] * (1 - aw) + (i == 0 ? 0 : 0.03f);
                    }
                    tailBaseX = -0.35f - 0.5f * kf(t, 0, 0, 7, 1, 10, 0.55f, 14, 0.7f, 22, 0);
                    hipZ += kf(t, 0, 0, 7, 1.5f, 11, -1.5f, 22, 0);
                    crouch += kf(t, 0, 0, 7, 1.5f, 11, 0, 22, 0);
                    chestX += kf(t, 0, 0, 7, -0.25f, 11, 0.35f, 22, 0);
                    headX += kf(t, 0, 0, 7, -0.2f, 11, 0.25f, 22, 0);
                    jawOpen += kf(t, 0, 0, 7, 0.7f, 22, 0.2f);
                    for (int s = 0; s < 2; s++) { A[s][0] = kf(t, 0, -0.6f, 8, -1.3f, 22, -0.6f); A[s][3] = 1.3f; A[s][1] = (s == 0 ? 1 : -1) * 0.5f; A[s][5] = aw * 0.8f; }
                }
                case TAIL_SWEEP -> {
                    float wind = kf(t, 0, 0, 10, 1f, 12, 1f, 14, 0, 22, 0, 28, 0);
                    rootSpin = kf(t, 0, 0, 9, 0.5f, 12, 0.7f, 20, 6.28f, 28, 6.28f);
                    for (int i = 0; i < TAIL; i++) {
                        tailYaw[i] = kf(t, 0, tailYaw[i], 9, 1.0f - i * 0.04f, 12, 0.5f, 20, -0.05f, 28, 0);
                        tailPitch[i] = kf(t, 0, tailPitch[i], 10, -0.03f * i, 20, 0.0f, 28, tailPitch[i]);
                    }
                    tailBaseX = kf(t, 0, tailBaseX, 10, -0.05f, 22, -0.05f, 28, tailBaseX);
                    crouch += kf(t, 0, 0, 9, 2.2f, 14, 3.5f, 22, 1.2f, 28, 0);
                    bodyY += -kf(t, 0, 0, 10, 0.55f, 12, 0.4f, 20, 0, 28, 0);
                    chestX += kf(t, 0, 0, 10, 0.2f, 20, 0.1f, 28, 0);
                    for (int s = 0; s < 2; s++) { A[s][0] = kf(t, 0, -0.6f, 9, -1.0f, 20, -1.2f, 28, -0.6f); A[s][1] = (s == 0 ? 1 : -1) * kf(t, 0, -0.3f, 10, -1.2f, 20, -1.3f, 28, -0.3f); A[s][2] = (s == 0 ? 1 : -1) * 0.5f * wind; A[s][3] = 0.6f; A[s][5] = aw; }
                    jawOpen += kf(t, 0, 0, 10, 0.5f, 20, 0.8f, 28, 0);
                    headX += kf(t, 0, 0, 10, -0.2f, 20, -0.2f, 28, 0);
                }
                case BITE -> {
                    headX += kf(t, 0, 0, 5, -0.6f, 8, 0.55f, 11, 0.5f, 18, 0);
                    neckX += kf(t, 0, 0, 5, -0.35f, 8, 0.4f, 11, 0.3f, 18, 0);
                    chestX += kf(t, 0, 0, 5, -0.3f, 8, 0.45f, 18, 0);
                    jawOpen += kf(t, 0, 0.1f, 4, 1.1f, 8, 1.3f, 9.5f, 0.1f, 18, 0.1f);
                    innerExt += kf(t, 0, 0, 5, 0, 8, 7.5f, 10, 7.5f, 13, 0, 18, 0);
                    hipZ -= kf(t, 0, 0, 5, -0.5f, 8, 2.0f, 18, 0);
                    for (int s = 0; s < 2; s++) { A[s][0] = kf(t, 0, -0.6f, 8, -1.6f, 18, -0.6f); A[s][3] = 0.8f; A[s][1] = (s == 0 ? 1 : -1) * 0.3f; A[s][5] = aw * 0.8f; }
                    headY += Mth.sin(t * 2.2f) * 0.05f * kf(t, 8, 0, 10, 1, 13, 0);
                }
                case GRAB -> {
                    float reach = kf(t, 0, 0, 5, 1, 8, 1, 11, 0);                          // reaching phase
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 5, -1.75f, 8, -1.65f, 12, -1.6f, 20, -2.15f, 24, -2.05f, 29, -2.1f, 41, -2.05f, 50, -2.1f, 54, -1.6f, 57, -1.2f, 60, -0.6f);
                        A[s][1] = sg * kf(t, 0, -0.2f, 5, -0.55f, 8, -0.45f, 12, 0.35f, 24, 0.45f, 50, 0.45f, 54, -0.8f, 60, -0.2f);
                        A[s][2] = sg * kf(t, 0, 0, 8, -0.2f, 12, 0.3f, 50, 0.3f, 57, -0.4f, 60, 0);
                        A[s][3] = kf(t, 0, 0.9f, 5, 0.2f, 8, 0.15f, 12, 1.0f, 20, 1.5f, 50, 1.5f, 54, 0.3f, 60, 0.9f);
                        A[s][4] = kf(t, 0, .3f, 5, -.2f, 8, .6f, 12, -.5f, 50, -.5f, 57, .8f, 60, .3f);
                        A[s][5] = aw;
                    }
                    chestX += kf(t, 0, 0, 6, 0.2f, 12, -0.15f, 24, -0.4f, 28, -0.45f, 30, 0.35f, 34, -0.3f, 41, -0.45f, 42, 0.35f, 46, -0.3f, 52, -0.2f, 54, 0.3f, 60, 0);
                    headX += kf(t, 0, 0, 12, 0, 25, -0.4f, 28.5f, -0.55f, 30, 0.6f, 34, 0.1f, 41, -0.5f, 42, 0.6f, 46, 0.1f, 54, 0, 60, 0);
                    neckX += kf(t, 0, 0, 25, -0.3f, 29, -0.4f, 30, 0.35f, 35, 0, 41, -0.35f, 42, 0.35f, 46, 0, 60, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 0.6f, 24, 1.0f, 29, 1.3f, 30.5f, 0.2f, 38, 0.9f, 41, 1.3f, 42.5f, 0.2f, 50, 0.4f, 54, 1.1f, 60, 0.1f);
                    innerExt += kf(t, 0, 0, 24, 0, 29, 0, 30.5f, 8.5f, 33, 8f, 36, 0, 41, 0, 42.5f, 8.5f, 45, 8f, 48, 0, 60, 0);
                    hipZ -= kf(t, 0, 0, 5, 1f, 8, -1.5f, 12, 0, 60, 0);
                    crouch += kf(t, 0, 0, 6, 1f, 10, 0.5f, 54, 0.5f, 60, 0);
                    chestScale = 1 + kf(t, 29, 0, 30.5f, 0.05f, 36, 0, 41, 0, 42.5f, 0.05f, 48, 0);
                    tailYaw[0] += Mth.sin(t * 0.5f) * 0.15f;
                    for (int i = 0; i < TAIL; i++) tailPitch[i] -= 0.04f * i * kf(t, 8, 0, 14, 1, 54, 1, 60, 0);
                }
                case POUNCE -> {
                    float cr = kf(t, 0, 0, 12, 5.5f, 14, 4f, 16, -1.5f, 22, -3f, 26, 0f, 30, 3.5f, 34, 0);
                    crouch += cr;
                    float air = kf(t, 13, 0, 15, 1, 25, 1, 28, 0);
                    legOv[0] = air; legOv[1] = 11f; legOv[2] = 11f;                      // legs trail back, toes pointed
                    P = Mth.lerp(air, P, 1.52f);
                    bodyX += kf(t, 0, 0, 12, 0.3f, 15, -0.15f, 25, 0.1f, 30, 0.3f, 34, 0);
                    chestX += kf(t, 0, 0, 12, 0.35f, 15, -0.3f, 25, -0.1f, 34, 0);
                    headX += kf(t, 0, 0, 12, 0.3f, 15, -0.2f, 25, 0.2f, 34, 0);
                    neckX += kf(t, 0, 0, 12, 0.2f, 15, -0.15f, 34, 0);
                    jawOpen += kf(t, 0, 0.1f, 11, 0.5f, 15, 1.35f, 25, 1.3f, 29, 0.2f, 34, 0.1f);
                    innerExt += kf(t, 0, 0, 14, 0, 17, 5, 25, 7, 29, 0, 34, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 11, 0.2f, 14, -0.6f, 17, -2.0f, 25, -1.95f, 29, -1.1f, 34, -0.6f);
                        A[s][1] = sg * kf(t, 0, -0.2f, 11, -0.4f, 17, -0.5f, 25, -0.45f, 29, 0.1f, 34, -0.2f);
                        A[s][2] = sg * kf(t, 0, 0, 17, -0.45f, 25, -0.4f, 34, 0);
                        A[s][3] = kf(t, 0, 0.9f, 11, 1.2f, 17, 0.1f, 25, 0.2f, 28, 1.0f, 34, 0.9f);
                        A[s][4] = kf(t, 0, .3f, 17, -.3f, 25, -.4f, 34, .3f);
                        A[s][5] = aw;
                    }
                    for (int i = 0; i < TAIL; i++) {
                        tailPitch[i] = kf(t, 0, tailPitch[i], 11, -0.2f - i * 0.03f, 15, 0.02f, 25, 0.04f - i * 0.01f, 34, tailPitch[i]);
                        tailYaw[i] = tailYaw[i] * (1 - aw * 0.8f);
                    }
                    tailBaseX = Mth.lerp(air, tailBaseX, 0.05f);
                }
                case SPIT -> {
                    float gather = kf(t, 0, 0, 10, 1, 12, 0, 20, 0);
                    headX += kf(t, 0, 0, 10, -0.75f, 12, 0.65f, 15, 0.45f, 26, 0);
                    neckX += kf(t, 0, 0, 10, -0.5f, 12, 0.5f, 26, 0);
                    chestX += kf(t, 0, 0, 10, -0.5f, 12, 0.3f, 26, 0);
                    jawOpen += kf(t, 0, 0.1f, 6, 0.8f, 11, 1.2f, 13, 1.35f, 18, 0.4f, 26, 0.1f);
                    innerExt += kf(t, 0, 0, 8, 2f, 11, 5f, 13, 6, 18, 0, 26, 0);
                    neckScale = 1 + 0.5f * gather;
                    chestScale = 1 + 0.12f * gather;
                    hipZ += kf(t, 0, 0, 10, 1.2f, 12, -1.2f, 26, 0);
                    crouch += kf(t, 0, 0, 10, 1.5f, 26, 0);
                    for (int s = 0; s < 2; s++) { A[s][0] = kf(t, 0, -0.6f, 10, -1.15f, 26, -0.6f); A[s][1] = (s == 0 ? 1 : -1) * 0.4f; A[s][3] = 1.2f; A[s][5] = aw * 0.8f; }
                    for (int i = 0; i < TAIL; i++) tailPitch[i] -= 0.05f * i * gather;
                }
                case SCREECH -> {
                    float in = kf(t, 0, 0, 10, 1, 16, 1, 40, 1, 44, 0);
                    float shake = Mth.sin(t * 3.1f) * kf(t, 14, 0, 17, 1, 38, 1, 44, 0);
                    headX += kf(t, 0, 0, 10, -0.95f, 16, -0.9f, 40, -0.85f, 44, 0);
                    neckX += kf(t, 0, 0, 10, -0.5f, 40, -0.45f, 44, 0);
                    chestX += kf(t, 0, 0, 10, -0.55f, 40, -0.5f, 44, 0);
                    jawOpen += kf(t, 0, 0.1f, 10, 0.6f, 16, 1.45f, 40, 1.4f, 44, 0.1f);
                    innerExt += kf(t, 0, 0, 12, 0, 17, 6.5f, 40, 6.5f, 44, 0) + shake * 0.8f;
                    headY += shake * 0.07f; headZ += shake * 0.05f; chestZ += shake * 0.03f; bodyY += shake * 0.04f;
                    chestScale = 1 + 0.16f * in;
                    crouch += kf(t, 0, 0, 10, 1.5f, 44, 0);
                    P = Mth.lerp(in * b, P, 0.1f);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 10, -1.2f, 16, -1.0f, 40, -1.0f, 44, -0.6f);
                        A[s][1] = sg * kf(t, 0, -0.2f, 10, -1.5f, 16, -1.65f, 40, -1.6f, 44, -0.2f) + shake * 0.05f;
                        A[s][2] = sg * kf(t, 0, 0, 10, -0.7f, 40, -0.7f, 44, 0);
                        A[s][3] = kf(t, 0, 0.9f, 10, 0.35f, 40, 0.35f, 44, 0.9f);
                        A[s][4] = shake * 0.2f; A[s][5] = aw;
                    }
                    for (int i = 0; i < TAIL; i++) { tailPitch[i] = -0.07f * i * in; tailYaw[i] += Mth.sin(t * 0.6f - i * 0.5f) * 0.25f * in; }
                    tailBaseX = Mth.lerp(in, tailBaseX, -0.5f);
                }
                case CHARGE -> {
                    float run = kf(t, 8, 0, 11, 1, 44, 1, 49, 0);
                    P = Mth.lerp(run, P, 1.5f);
                    headX += kf(t, 0, 0, 8, 0.55f, 12, 0.75f, 46, 0.7f, 50, 0);
                    neckX += kf(t, 0, 0, 8, 0.4f, 46, 0.4f, 50, 0);
                    crouch += kf(t, 0, 0, 8, 2.5f, 46, 2.5f, 50, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 1.0f, 12, 1.3f, 46, 1.3f, 50, 0.1f);
                    // pawing: alternate forelegs scrape the ground while winding up
                    float paw = kf(t, 0, 1, 8, 1, 10, 0);
                    for (int s = 0; s < 2; s++) {
                        float ps = Mth.sin(t * 1.9f + s * Mth.PI);
                        A[s][0] = -1.57f + P * 0.0f - 0.5f + ps * 0.7f * paw - (1 - paw) * 0.2f;
                        A[s][3] = 0.7f + Math.max(0, ps) * paw; A[s][1] = 0; A[s][5] = aw * paw;
                    }
                    legSpeed = 1.8f;
                    chestScale = 1 + 0.08f * run;
                }
                case IMPALE -> {
                    float coil = kf(t, 0, 0, 9, 1f, 15, 0.85f, 18, 1.15f, 30, 1.15f, 34, 0.3f);
                    for (int i = 0; i < TAIL; i++) {
                        tailPitch[i] = -(0.12f + 0.05f * i) * kf(t, 0, 0.1f, 9, 1.0f, 15, 1.55f, 18, 1.2f, 30, 1.3f, 32, 0.2f, 34, 0);
                        tailYaw[i] *= (1 - aw);
                    }
                    tailBaseX = -0.5f - 0.5f * coil;
                    chestX += kf(t, 0, 0, 9, -0.4f, 15, 0.4f, 18, 0.1f, 30, 0.1f, 34, 0);
                    headX += kf(t, 0, 0, 9, -0.3f, 15, 0.3f, 18, -0.35f, 30, -0.4f, 32, 0.3f, 34, 0);
                    jawOpen += kf(t, 0, .1f, 9, .8f, 18, 1.3f, 30, 1.3f, 34, .2f);
                    innerExt += kf(t, 0, 0, 18, 0, 20, 7, 30, 7, 33, 0);
                    crouch += kf(t, 0, 0, 9, 2f, 15, -0.5f, 34, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 9, -1.7f, 15, -2.1f, 30, -2.4f, 34, -0.6f); A[s][3] = 0.5f;
                        A[s][1] = sg * kf(t, 0, -.2f, 9, -.9f, 30, -1.1f, 34, -.2f); A[s][2] = sg * -0.4f * aw; A[s][5] = aw;
                    }
                }
                case SLAM -> {
                    float up = kf(t, 0, 0, 11, 1, 13, 0.8f, 14, 0, 30, 0);
                    float rear = kf(t, 0, 0, 11, 1, 13, 1, 15, 0, 24, 0);
                    P = Mth.lerp(rear * 0.9f, P, 0.08f);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 11, -3.0f, 14, -0.85f, 17, -0.95f, 30, -0.6f);
                        A[s][1] = sg * kf(t, 0, -0.2f, 11, 0.1f, 14, 0.15f, 17, 0.1f, 30, -0.2f);
                        A[s][2] = sg * kf(t, 0, 0, 11, -0.35f, 14, 0, 30, 0);
                        A[s][3] = kf(t, 0, 0.9f, 11, 0.3f, 14, 0.1f, 17, 0.3f, 30, 0.9f);
                        A[s][4] = kf(t, 0, .3f, 11, -.4f, 14, .9f, 30, .3f);
                        A[s][5] = aw;
                    }
                    chestX += kf(t, 0, 0, 11, -0.45f, 14, 0.75f, 17, 0.55f, 30, 0);
                    headX += kf(t, 0, 0, 11, -0.6f, 14, 0.7f, 30, 0);
                    neckX += kf(t, 0, 0, 11, -0.3f, 14, 0.4f, 30, 0);
                    jawOpen += kf(t, 0, .1f, 11, 1.3f, 14, 1.0f, 22, .3f, 30, .1f);
                    hipZ += kf(t, 0, 0, 11, 2f, 14, -2.5f, 30, 0);
                    crouch += kf(t, 0, 0, 11, -0.5f, 14, 3.5f, 20, 2f, 30, 0);
                    float quake = Mth.sin(t * 4f) * kf(t, 14, 1, 22, 0);
                    bodyX += quake * 0.04f; chestZ += quake * 0.03f;
                    for (int i = 0; i < TAIL; i++) { tailPitch[i] = kf(t, 0, tailPitch[i], 11, -0.06f * i, 14, 0.05f, 30, tailPitch[i]); }
                }
                case LAY -> {
                    float sq = kf(t, 0, 0, 12, 1, 58, 1, 70, 0);
                    float pulse = Mth.sin(t * 0.55f) * kf(t, 12, 0, 20, 1, 50, 1, 60, 0);
                    crouch += 3.5f * sq + pulse * 0.6f;
                    P = Mth.lerp(sq, P, 0.2f);
                    hipZ += 1.5f * sq;
                    chestX += -0.3f * sq + pulse * 0.03f;
                    headX += -0.5f * sq + Mth.sin(t * 0.3f) * 0.05f * sq;
                    jawOpen += 0.5f * sq + pulse * 0.2f;
                    chestScale = 1 + 0.04f * pulse;
                    for (int i = 0; i < TAIL; i++) { tailPitch[i] = -0.18f * sq - 0.02f * i; tailYaw[i] *= (1 - sq); }
                    tailBaseX = -0.9f * sq + tailBaseX * (1 - sq);
                    for (int s = 0; s < 2; s++) { float sg = s == 0 ? 1 : -1; A[s][0] = -0.9f; A[s][1] = sg * 0.1f; A[s][3] = 1.8f; A[s][5] = aw * 0.9f; }
                    float tw = kf(t, 30, 0, 35, 1, 45, 0);
                    hips.zScale = 1 + 0.12f * tw; hips.yScale = 1 + 0.12f * tw;
                }
                default -> {}
            }
        }

        // ---------- apply stance/pose to parts
        float hAct = H - crouch;
        hips.y = 24 - hAct + bob;
        hips.z = 1 + hipZ + (1 - b) * -1.5f;
        hips.zRot = walkRoll;
        // quad body is long: shorten "bipedal" crouch effect on hips height etc handled by IK
        body.xRot = P + bodyX; body.yRot = bodyY; body.zRot = walkRoll * -1.5f;
        float aim = P - Mth.lerp(b, 0.15f, 0.55f);                    // how much the head must tilt back up
        chest.xRot = chestX - aim * 0.22f; chest.yRot = chestY; chest.zRot = chestZ;
        neck.xRot = neckX - aim * 0.42f; neck.yRot = neckY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.35f;
        head.xRot = headX - aim * 0.34f + Mth.clamp(headPitch, -45, 45) * Mth.DEG_TO_RAD * 0.7f;
        head.yRot = headY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.3f; head.zRot = headZ;
        jaw.xRot = Mth.clamp(jawOpen, 0, 1.5f);
        inner.z = -3.5f - innerExt; inner.xRot = jaw.xRot * 0.15f;
        chest.xScale = chestScale; chest.yScale = chestScale; chest.zScale = chestScale;
        neck.xScale = neck.zScale = neckScale;
        root.yRot = rootSpin;
        tubeL.xRot = 0.25f + breathe * 0.03f - und * 0.2f; tubeR.xRot = tubeL.xRot;
        tubeL.yRot = 0.1f + und * 0.3f; tubeR.yRot = -tubeL.yRot;

        // extra Queen arms flutter
        for (int s = 0; s < 2; s++) {
            float sg = s == 0 ? 1 : -1;
            arm2[s].xRot = -0.9f + Mth.sin(age * 0.07f + s * 2) * 0.12f + (at == Attack.NONE ? 0 : -0.6f * aw);
            arm2[s].zRot = sg * -0.25f; fore2[s].xRot = -1.0f + Mth.sin(age * 0.09f + s) * 0.15f;
        }

        // tail
        tail0.xRot = tailBaseX; tail0.yRot = tailYaw[0];
        for (int i = 1; i < TAIL; i++) { tail[i].xRot = tailPitch[i]; tail[i].yRot = tailYaw[i]; }
        tail0.xRot += tailPitch[0];

        // ---------- legs via IK
        float footH = hAct - 1.6f;
        for (int s = 0; s < 2; s++) {
            float u = ((ph * legSpeed / Mth.TWO_PI) + (s == 0 ? 0 : 0.5f)) % 1f; if (u < 0) u += 1;
            float stride = Mth.lerp(b, 4.8f, 5.6f) * spd;
            float tz, lift;
            if (u < 0.58f) { tz = Mth.lerp(u / 0.58f, -stride, stride); lift = 0; }
            else { float k = (u - 0.58f) / 0.42f; tz = Mth.lerp(smooth(k), stride, -stride); lift = Mth.sin(k * Mth.PI) * (1.6f + 1.8f * spd); }
            tz += Mth.lerp(b, 0.5f, 1.0f);                           // keep feet slightly forward of hip
            float ty = footH - lift;
            tz = Mth.lerp(legOv[0], tz, legOv[1]); ty = Mth.lerp(legOv[0], ty, legOv[2] + footH * 0.0f);
            float[] a = ik(tz, ty, 8f, 8f, true);
            thigh[s].xRot = a[0]; shin[s].xRot = a[1] - a[0];
            foot[s].xRot = -a[1] + Mth.lerp(legOv[0], 0.0f, 0.9f) + lift * 0.06f;
            thigh[s].zRot = (s == 0 ? -1 : 1) * 0.06f; 
        }

        // ---------- arms: quad -> IK to ground, biped -> authored pose; attack weight overrides
        float shoulderUp = 15f * Mth.cos(P);
        float shoulderZ = -1 - 15f * Mth.sin(P);
        for (int s = 0; s < 2; s++) {
            float u = ((ph / Mth.TWO_PI) + (s == 0 ? 0.5f : 0f)) % 1f; if (u < 0) u += 1;
            float stride = 5.2f * spd;
            float gz, lift;
            if (u < 0.58f) { gz = Mth.lerp(u / 0.58f, -stride, stride); lift = 0; }
            else { float k = (u - 0.58f) / 0.42f; gz = Mth.lerp(smooth(k), stride, -stride); lift = Mth.sin(k * Mth.PI) * (2f + 2f * spd); }
            float wz = shoulderZ - 3.5f + gz;                                // hand ground contact (hips frame z)
            float[] q = ik(wz - shoulderZ, Math.max(1f, hAct + shoulderUp - 1.6f - lift), 6.6f, 7.5f, false);
            float qPitch = q[0], qEl = q[1] - q[0], qHand = -q[1];
            float wq = 1 - b;
            float pitch = Mth.lerp(wq, A[s][0], qPitch);        // A[][0] is world pitch; IK result is world too
            float el = Mth.lerp(wq, A[s][3], qEl);
            float hnd = Mth.lerp(wq, A[s][4], qHand);
            float yaw = Mth.lerp(wq, A[s][1], (s == 0 ? 1 : -1) * -0.05f);
            float roll = Mth.lerp(wq, A[s][2], 0);
            // attack override (world values)
            float w = A[s][5];
            if (at != Attack.NONE) {
                pitch = Mth.lerp(w, pitch, A[s][0]); el = Mth.lerp(w, el, A[s][3]); hnd = Mth.lerp(w, hnd, A[s][4]);
                yaw = Mth.lerp(w, yaw, A[s][1]); roll = Mth.lerp(w, roll, A[s][2]);
            }
            arm[s].xRot = pitch - body.xRot - chest.xRot; arm[s].yRot = yaw; arm[s].zRot = roll;
            fore[s].xRot = el; hand[s].xRot = hnd;
        }
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float bl, float a) {
        root.render(ps, vc, light, overlay, r, g, bl, a);
    }
}
