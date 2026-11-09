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

    /** Per-caste silhouette parameters. Limb LENGTHS are fixed (IK depends on them); everything else varies. */
    static final class Sp {
        float domeL = 12, domeH = 4.5f, domeW = 4.4f, headW = 1, torso = 1, limb = 1, tailT = 1, clawL = 1, fan = 12, armK = 1, legK = 1, neckK = 1, upright = 0;
        int style, tailSegs = 8, tailEnd, ribs = 3, spines, plates, horns, tubeLen = 11;
        boolean bare, cheek, mand, carapace, sac, belly, knee, blade, elbow, dreads, brow;
    }
    // style: 0 smooth dome, 1 ridged, 2 crowned fan, 3 frill+horns, 4 bulb (pathogen/neomorph), 5 spiked crest
    // tailEnd: 0 blade, 1 spear, 2 club, 3 twin-blade, 4 bare tip
    static Sp spec(Caste c) {
        Sp p = new Sp();
        switch (c) {
            case DRONE -> { p.spines = 1; }
            case WARRIOR -> { p.style = 1; p.ribs = 4; p.spines = 3; p.clawL = 1.15f; p.brow = true; }
            case PRAETORIAN -> { p.style = 2; p.fan = 13; p.limb = 1.2f; p.torso = 1.2f; p.plates = 1; p.spines = 4; p.horns = 2; p.elbow = true; p.tailEnd = 1; }
            case QUEEN -> { p.style = 2; p.fan = 17; p.domeL = 14; p.torso = 1.15f; p.carapace = true; p.spines = 5; p.ribs = 4; p.plates = 1; p.horns = 3; p.limb = 1.1f; }
            case MATRIARCH -> { p.style = 2; p.fan = 14; p.domeL = 16; p.domeH = 5; p.limb = 0.9f; p.spines = 7; p.sac = true; p.horns = 2; p.tailEnd = 3; p.clawL = 1.3f; p.belly = true; }
            case EMPRESS -> { p.style = 2; p.fan = 20; p.domeL = 13; p.carapace = true; p.plates = 2; p.horns = 5; p.tailEnd = 1; p.torso = 1.25f; p.elbow = true; p.knee = true; p.ribs = 5; }
            case QUEEN_MOTHER -> { p.style = 2; p.fan = 24; p.domeL = 18; p.domeH = 6; p.domeW = 5.6f; p.carapace = true; p.plates = 2; p.horns = 7; p.spines = 9; p.belly = true; p.elbow = true; p.knee = true; p.torso = 1.35f; p.limb = 1.25f; p.tailEnd = 3; p.cheek = true; p.mand = true; }
            case RUNNER -> { p.domeL = 9; p.domeH = 3.6f; p.domeW = 3.6f; p.limb = 0.8f; p.torso = 0.85f; p.tubeLen = 7; p.cheek = true; p.clawL = 1.2f; p.ribs = 2; p.tailEnd = 4; }
            case CRUSHER -> { p.style = 3; p.horns = 3; p.carapace = true; p.limb = 1.35f; p.torso = 1.3f; p.tailSegs = 6; p.tailEnd = 2; p.plates = 2; p.knee = true; p.domeL = 7; p.domeH = 5; p.domeW = 5; p.ribs = 1; }
            case SPITTER -> { p.domeL = 11; p.domeH = 4; p.sac = true; p.cheek = true; p.spines = 2; p.tailEnd = 1; }
            case RAVAGER -> { p.style = 1; p.blade = true; p.elbow = true; p.spines = 7; p.cheek = true; p.clawL = 1.2f; p.tailEnd = 3; p.ribs = 4; p.brow = true; }
            case LURKER -> { p.domeL = 14; p.domeH = 4.2f; p.limb = 0.9f; p.clawL = 1.6f; p.torso = 0.95f; p.tubeLen = 0; p.ribs = 5; }
            case SENTINEL -> { p.style = 5; p.spines = 4; p.horns = 2; p.domeL = 10; p.limb = 0.95f; p.cheek = true; p.tailEnd = 1; }
            case CARRIER -> { p.belly = true; p.domeL = 12; p.limb = 1.1f; p.torso = 1.15f; p.ribs = 2; p.plates = 1; }
            case PREDALIEN -> { p.dreads = true; p.domeL = 8; p.domeW = 5.4f; p.domeH = 5; p.mand = true; p.torso = 1.25f; p.plates = 1; p.spines = 3; p.ribs = 4; p.limb = 1.15f; p.cheek = true; p.brow = true; }
            case NEOMORPH -> { p.style = 4; p.domeW = 4.6f; p.armK = 1.25f; p.legK = 1.1f; p.neckK = 1.25f; p.upright = 0.15f; p.limb = 0.7f; p.torso = 0.75f; p.tailSegs = 3; p.tailEnd = 4; p.spines = 6; p.ribs = 0; p.tubeLen = 0; p.clawL = 1.1f; }
            // gaunt, long-limbed, skull-faced humanoid: swept-back smooth cranium, hanging arms, bare ribcage, no tail
            case NEWBORN -> { p.style = 0; p.domeL = 10; p.domeH = 3.4f; p.domeW = 3.2f; p.headW = 0.85f; p.limb = 0.6f; p.torso = 0.62f; p.armK = 1.5f; p.legK = 1.25f; p.neckK = 1.6f; p.upright = 0.3f; p.bare = true; p.clawL = 1.6f; p.tailSegs = 0; p.ribs = 8; p.spines = 0; p.tubeLen = 0; }
            case DEACON -> { p.domeL = 7; p.domeH = 4; p.limb = 0.9f; p.tailSegs = 5; p.tailEnd = 4; p.ribs = 1; p.tubeLen = 0; }
            case BOILER -> { p.belly = true; p.sac = true; p.domeL = 10; p.torso = 1.35f; p.spines = 8; p.limb = 1.1f; p.tailEnd = 4; p.ribs = 2; }
            case PROTOMORPH -> { p.style = 4; p.domeW = 4.4f; p.ribs = 1; p.spines = 5; p.limb = 0.95f; p.tailEnd = 1; p.cheek = true; p.tubeLen = 0; }
            case STALKER -> { p.style = 5; p.clawL = 1.45f; p.spines = 6; p.cheek = true; p.domeL = 13; p.limb = 0.95f; p.tailEnd = 1; p.blade = true; }
            case KING -> { p.style = 3; p.horns = 2; p.carapace = true; p.plates = 2; p.elbow = true; p.knee = true; p.torso = 1.2f; p.limb = 1.2f; p.domeL = 9; p.domeH = 5; p.ribs = 4; p.spines = 5; p.tubeLen = 0; }
            case CHARGER -> { p.style = 3; p.horns = 4; p.carapace = true; p.limb = 1.3f; p.tailSegs = 5; p.tailEnd = 2; p.domeL = 6; p.domeH = 5; p.domeW = 5; p.torso = 1.1f; p.tubeLen = 0; p.ribs = 0; }
            case BRUTE -> { p.style = 3; p.horns = 1; p.plates = 2; p.limb = 1.4f; p.torso = 1.35f; p.knee = true; p.elbow = true; p.tailEnd = 2; p.tailSegs = 6; p.domeL = 8; p.domeH = 5.5f; p.domeW = 5.2f; p.carapace = true; }
            case BULL_ALIEN -> { p.style = 3; p.horns = 6; p.torso = 1.25f; p.limb = 1.2f; p.cheek = true; p.brow = true; p.domeL = 8; p.domeH = 5; p.domeW = 5; p.plates = 1; p.tailEnd = 0; p.ribs = 4; }
            case PATHOGEN_RUNNER -> { p.style = 4; p.limb = 0.8f; p.torso = 0.85f; p.tailSegs = 6; p.spines = 5; p.domeW = 4f; p.tailEnd = 4; p.ribs = 0; p.tubeLen = 0; p.clawL = 1.2f; }
            case PATHOGEN_BLIGHT -> { p.style = 4; p.sac = true; p.mand = true; p.domeW = 4.8f; p.spines = 4; p.belly = true; p.tailEnd = 4; p.tubeLen = 0; p.ribs = 0; }
            case PATHOGEN_BRUTE -> { p.style = 4; p.torso = 1.45f; p.limb = 1.3f; p.spines = 7; p.knee = true; p.tailSegs = 5; p.tailEnd = 2; p.domeW = 5.8f; p.ribs = 0; p.tubeLen = 0; p.plates = 1; }
            case PATHOGEN_QUEEN -> { p.style = 4; p.domeW = 6f; p.horns = 5; p.carapace = true; p.sac = true; p.torso = 1.2f; p.spines = 6; p.tubeLen = 0; p.ribs = 0; p.belly = true; p.tailEnd = 1; }
            case BURSTER -> { p.domeL = 7; p.domeH = 4; p.sac = true; p.spines = 5; p.limb = 1.1f; p.tailSegs = 4; p.tailEnd = 4; p.ribs = 0; p.belly = true; p.tubeLen = 0; }
            case PROWLER -> { p.style = 1; p.domeL = 13; p.limb = 0.9f; p.clawL = 1.35f; p.tailEnd = 1; p.spines = 3; p.blade = true; p.ribs = 5; p.tubeLen = 8; p.torso = 0.95f; }
            case ARACHNID -> { p.domeL = 6; p.domeH = 3.2f; p.domeW = 5.6f; p.mand = true; p.cheek = true; p.spines = 8; p.tailEnd = 3; p.ribs = 2; p.tubeLen = 0; p.elbow = true; p.headW = 1.15f; }
            default -> {}
        }
        return p;
    }

    public static LayerDefinition create(Caste c) {
        Sp sp = spec(c);
        cu = cv = rowH = 0;
        float T = sp.torso, L = sp.limb;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", add(box(-3.5f * T, -3f, -4f, 7 * T, 6, 9), -2.5f, -4, -1, 5, 1.5f, 5), PartPose.offset(0, 11, 1));

        // torso
        CubeListBuilder bodyB = box(-3 * T, -9, -3, 6 * T, 10, 6);
        add(bodyB, -1, -9, 2.8f, 2, 9, 1.5f);
        if (sp.belly) add(bodyB, -3.8f * T, -7, -5, 7.6f * T, 8, 2.4f);               // distended belly
        PartDefinition body = hips.addOrReplaceChild("body", bodyB, PartPose.offset(0, -1, -1));
        CubeListBuilder chestB = box(-3.8f * T, -8.5f, -3.8f, 7.6f * T, 9, 7.6f);
        for (int i = 0; i < sp.ribs; i++) { float y = -7.5f + i * (8f / Math.max(1, sp.ribs)); add(chestB, -4.4f * T, y, -3, 0.9f, 1.1f, 6); add(chestB, 3.5f * T, y, -3, 0.9f, 1.1f, 6); }
        add(chestB, -1, -8.5f, 3.7f, 2, 8, 1.5f);
        for (int i = 0; i < sp.spines; i++) { float y = -8.2f + i * (8.4f / Math.max(1, sp.spines)); add(chestB, -0.5f, y, 5.1f, 1, 1.4f, 2.2f + (i % 2) * 1.2f); }
        for (int i = 0; i < sp.plates; i++) {
            add(chestB, -5.6f * T, -9f, -3.2f + i * 0.4f, 3.2f, 1.6f, 6.4f); add(chestB, 2.4f * T, -9f, -3.2f + i * 0.4f, 3.2f, 1.6f, 6.4f);   // pauldrons
            if (i == 1) { add(chestB, -6.2f * T, -9.8f, -2.2f, 1.2f, 3f, 1.2f); add(chestB, 5f * T, -9.8f, -2.2f, 1.2f, 3f, 1.2f); }          // shoulder spikes
        }
        if (sp.carapace) { add(chestB, -4.4f * T, -9.2f, 3.6f, 8.8f * T, 10.5f, 2.2f); add(chestB, -5f * T, -9.8f, 3.2f, 10f * T, 1.6f, 3f); }
        if (sp.belly) add(chestB, -4.4f * T, -2.5f, -4.8f, 8.8f * T, 3.5f, 2f);
        PartDefinition chest = body.addOrReplaceChild("chest", chestB, PartPose.offset(0, -8.5f, 0));
        PartDefinition neck = chest.addOrReplaceChild("neck", box(-2 * L, -5, -2 * L, 4 * L, 6, 4 * L), PartPose.offset(0, -7.5f, 0));

        // head
        float hw = sp.headW;
        CubeListBuilder headB = box(-2.4f * hw, -3.5f, -6, 4.8f * hw, 4.5f, 6);
        add(headB, -1.5f, -4.5f, -4, 3, 1, 4);
        if (sp.brow) add(headB, -2.7f * hw, -4.6f, -5.8f, 5.4f * hw, 1.1f, 2.5f);
        PartDefinition head = neck.addOrReplaceChild("head", headB, PartPose.offset(0, -4.5f, 0));
        CubeListBuilder domeB = CubeListBuilder.create();
        if (sp.style != 4) { box2(domeB, -sp.domeW / 2, -1 - sp.domeH, -1, sp.domeW, sp.domeH, sp.domeL); add(domeB, -sp.domeW * 0.32f, -sp.domeH - 1.7f, 2, sp.domeW * 0.64f, 1, sp.domeL * 0.66f); }
        head.addOrReplaceChild("dome", domeB, PartPose.ZERO);
        CubeListBuilder rid = CubeListBuilder.create();
        if (sp.style == 1) for (int i = 0; i < 3; i++) box2(rid, -0.7f, -sp.domeH - 2.6f, 0.5f + i * 3.6f, 1.4f, 1.4f, 3);
        if (sp.style == 5) for (int i = 0; i < 5; i++) box2(rid, -0.5f, -sp.domeH - 4f + (i % 2) * 0.8f, 0.5f + i * 2.2f, 1, 3.2f - (i % 2) * 0.8f, 1);
        if (sp.cheek) { box2(rid, -3.6f * hw, -2.4f, -4.5f, 1, 1, 3.5f); box2(rid, 2.6f * hw, -2.4f, -4.5f, 1, 1, 3.5f); box2(rid, -3.2f * hw, -1.2f, -2.5f, 0.8f, 0.8f, 2.5f); box2(rid, 2.4f * hw, -1.2f, -2.5f, 0.8f, 0.8f, 2.5f); }
        head.addOrReplaceChild("ridges", rid, PartPose.ZERO);
        CubeListBuilder fanB = CubeListBuilder.create();
        if (sp.style == 2) {
            float fw = sp.fan;
            box2(fanB, -fw / 2, -11, 4.5f, fw, 9, 1); box2(fanB, -fw / 3, -13.5f, 4.5f, fw * 0.66f, 3, 1);
            for (int i = 0; i < sp.horns; i++) { float x = -fw / 2 + 0.5f + i * ((fw - 1) / Math.max(1, sp.horns - 1)); box2(fanB, x - 0.4f, -14.5f - (i % 2), 4.6f, 0.8f, 4, 0.8f); }
        }
        head.addOrReplaceChild("fan", fanB, PartPose.rotation(0.25f, 0, 0));
        CubeListBuilder hornB = CubeListBuilder.create();
        if (sp.style == 3) {
            box2(hornB, -6.5f, -9, 0, 13, 9, 1.5f); box2(hornB, -7.5f, -3.5f, -3, 2, 2, 7); box2(hornB, 5.5f, -3.5f, -3, 2, 2, 7);
            for (int i = 0; i < sp.horns; i++) { float x = sp.horns == 1 ? -1 : -4 + i * (8f / Math.max(1, sp.horns - 1)); box2(hornB, x, -10.5f, 0.2f, 2, 1.8f + (i % 2) * 1.5f, 1.2f); box2(hornB, x * 1.1f, -4.5f, -7.5f + (i % 2), 1.2f, 1.2f, 3.5f); }
        }
        head.addOrReplaceChild("horns", hornB, PartPose.offsetAndRotation(0, -1, -1.5f, -0.1f, 0, 0));
        CubeListBuilder dr = CubeListBuilder.create();
        if (sp.dreads) for (int i = -3; i <= 3; i++) box2(dr, i * 1.1f, -1, 0, 0.9f, 8 + (i & 1) * 3 + 2, 0.9f);
        head.addOrReplaceChild("dreads", dr, PartPose.offsetAndRotation(0, -3.5f, 8, 0.5f, 0, 0));
        CubeListBuilder bulbB = CubeListBuilder.create();
        if (sp.style == 4) {
            float bw = sp.domeW + 1.6f; box2(bulbB, -bw / 2, -7.5f, -4, bw, 8, 8); box2(bulbB, -bw * 0.35f, -9.2f, -1, bw * 0.7f, 2, 5.5f);
            box2(bulbB, -2, -3.4f, -6.5f, 4, 3, 2.5f);
            for (int i = 0; i < sp.horns; i++) { float x = -4 + i * (8f / Math.max(1, sp.horns - 1)); box2(bulbB, x, -11f - (i % 2), 0.5f, 0.9f, 3f, 0.9f); }
        }
        head.addOrReplaceChild("bulb", bulbB, PartPose.ZERO);

        CubeListBuilder jawB = box(-2 * hw, 0, -6, 4 * hw, 1.5f, 8);
        for (int i = 0; i < (sp.bare ? 0 : 4); i++) { tooth(jawB, -1.8f * hw, -1, -5.5f + i * 1.6f, 0.5f, 1.1f, 0.5f); tooth(jawB, 1.3f * hw, -1, -5.5f + i * 1.6f, 0.5f, 1.1f, 0.5f); }
        if (sp.sac) box2(jawB, -2.6f, 1.4f, -4.5f, 5.2f, 3.2f, 6);
        head.addOrReplaceChild("jaw", jawB, PartPose.offset(0, 1, 1));
        CubeListBuilder inB = box(-1, -0.7f, -4, 2, 1.4f, 14);
        tooth(inB, -0.7f, -0.5f, -5.2f, 1.4f, 1.0f, 1.2f); tooth(inB, -0.5f, -1.2f, -4.8f, 1, 0.6f, 0.6f); tooth(inB, -0.5f, 0.6f, -4.6f, 1, 0.6f, 0.6f);
        head.addOrReplaceChild("inner", inB, PartPose.offset(0, 0.4f, -1.5f));
        CubeListBuilder upT = CubeListBuilder.create();
        for (int i = 0; i < (sp.bare ? 0 : 4); i++) { tooth(upT, -2 * hw, 0.9f, -5.7f + i * 1.5f, 0.5f, 1.2f, 0.5f); tooth(upT, 1.5f * hw, 0.9f, -5.7f + i * 1.5f, 0.5f, 1.2f, 0.5f); }
        if (sp.mand) { box2(upT, -4f * hw, -0.5f, -8f, 1.3f, 1.3f, 6); box2(upT, 2.7f * hw, -0.5f, -8f, 1.3f, 1.3f, 6); box2(upT, -3.2f * hw, -0.4f, -9.5f, 0.8f, 0.8f, 2); box2(upT, 2.4f * hw, -0.4f, -9.5f, 0.8f, 0.8f, 2); }
        head.addOrReplaceChild("upperTeeth", upT, PartPose.ZERO);

        // back pipes
        float tl = Math.max(0.1f, sp.tubeLen);
        chest.addOrReplaceChild("tubeL", pipe(-0.9f, -1, 0, 1.8f, 1.8f, tl), PartPose.offsetAndRotation(2 * T, -6.5f, 3.6f, 0.25f, 0.1f, 0));
        chest.addOrReplaceChild("tubeR", pipe(-0.9f, -1, 0, 1.8f, 1.8f, tl), PartPose.offsetAndRotation(-2 * T, -6.5f, 3.6f, 0.25f, -0.1f, 0));

        // arms (index 0 = left / +x)
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            CubeListBuilder ab = box(-1.6f * L, -1, -1.6f * L, 3.2f * L, 9.5f, 3.2f * L);
            if (sp.elbow) add(ab, -0.5f, 6.5f, 1.4f * L, 1, 1, 3.4f);
            PartDefinition a = chest.addOrReplaceChild("arm" + n, ab, PartPose.offset(sx * 5 * T, -6.5f, 0));
            CubeListBuilder fb = box(-1.3f * L, 0, -1.3f * L, 2.6f * L, 10f, 2.6f * L);
            if (sp.blade) add(fb, -0.4f, 0.5f, 1.1f * L, 0.8f, 10.5f, 1.6f);
            PartDefinition f = a.addOrReplaceChild("fore", fb, PartPose.offset(0, 8.5f, 0));
            float fl = 3.8f * sp.clawL;
            CubeListBuilder h = box(-1.6f * L, 0, -2.5f, 3.2f * L, 1.6f, 4.5f);
            for (int k = -1; k <= 1; k++) add(h, k * 1.0f * L - 0.35f, 0.3f, -2.4f - fl, 0.7f, 0.9f, fl);
            f.addOrReplaceChild("hand", h, PartPose.offset(0, 10f, 0));
            PartDefinition a2 = chest.addOrReplaceChild("arm2" + n, box(-1, -1, -1, 2, 5, 2), PartPose.offset(sx * 3.6f * T, -3.5f, -2.5f));
            a2.addOrReplaceChild("fore", add(box(-0.8f, 0, -0.8f, 1.6f, 5, 1.6f), -1.2f, 5, -2.2f, 2.4f, 1, 3), PartPose.offset(0, 4.5f, 0));
        }
        // legs
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            CubeListBuilder tb = box(-2.2f * L, -1, -2.3f * L, 4.4f * L, 9.5f, 4.6f * L);
            if (sp.knee) add(tb, -0.5f, 7f, -3.2f * L - 2, 1, 1.4f, 2.4f);
            PartDefinition t = hips.addOrReplaceChild("thigh" + n, tb, PartPose.offset(sx * 4.2f * T, 0, 1));
            PartDefinition sh = t.addOrReplaceChild("shin", box(-1.5f * L, -0.5f, -1.5f * L, 3 * L, 8.5f, 3 * L), PartPose.offset(0, 8, 0));
            CubeListBuilder ft = box(-1.8f * L, 0, -5, 3.6f * L, 1.6f, 6.5f);
            for (int k = -1; k <= 1; k++) add(ft, k * 1.1f * L - 0.4f, 0.4f, -7.5f, 0.8f, 1, 3);
            sh.addOrReplaceChild("foot", ft, PartPose.offset(0, 8, 0));
        }
        // tail
        int N = Math.max(1, sp.tailSegs);
        float taper = 0.42f * 8f / N;
        PartDefinition tp = hips.addOrReplaceChild("tail0", box(-2.4f * sp.tailT, -2.4f * sp.tailT, 0, 4.8f * sp.tailT, 4.8f * sp.tailT, 7), PartPose.offset(0, -0.5f, 4.5f));
        for (int i = 1; i < TAIL; i++) {
            float w = Math.max(1.2f, (4.4f - i * taper) * sp.tailT);
            CubeListBuilder cb = box(-w / 2, -w / 2, 0, w, w, 7);
            if (i == N - 1) tailEnd(cb, sp.tailEnd, w);
            tp = tp.addOrReplaceChild("tail" + i, cb, PartPose.offset(0, 0, 7));
        }
        return LayerDefinition.create(mesh, 256, 128);
    }

    private static CubeListBuilder box2(CubeListBuilder b, float x, float y, float z, float w, float h, float d) { return add(b, x, y, z, w, h, d); }

    private static void tailEnd(CubeListBuilder cb, int kind, float w) {
        switch (kind) {
            case 0 -> { add(cb, -0.4f, -3.4f, 3, 0.8f, 6.8f, 6); add(cb, -0.4f, -2.2f, 8, 0.8f, 4.4f, 4); }                       // leaf blade
            case 1 -> { add(cb, -0.5f, -0.5f, 5, 1, 1, 10); add(cb, -0.4f, -1.6f, 8, 0.8f, 3.2f, 1.2f); add(cb, -0.4f, -1.2f, 11.5f, 0.8f, 2.4f, 1.2f); } // spear + barbs
            case 2 -> { add(cb, -3f, -3f, 1, 6, 6, 6); add(cb, -0.6f, -4.6f, 2, 1.2f, 1.6f, 1.2f); add(cb, -0.6f, 3f, 2, 1.2f, 1.6f, 1.2f); add(cb, -4.6f, -0.6f, 2, 1.6f, 1.2f, 1.2f); add(cb, 3f, -0.6f, 2, 1.6f, 1.2f, 1.2f); } // spiked club
            case 3 -> { add(cb, -2.2f, -2.8f, 3, 0.8f, 5.6f, 6); add(cb, 1.4f, -2.8f, 3, 0.8f, 5.6f, 6); add(cb, -0.4f, -1.2f, 6, 0.8f, 2.4f, 5); }  // twin blade
            default -> { }
        }
    }

    // ------------------------------------------------------------------ animation
    private float P;  // current chest pitch (world, rad)

    @Override
    public void setupAnim(XenoEntity e, float limbSwing, float amt, float age, float headYaw, float headPitch) {
        float pt = Minecraft.getInstance().getFrameTime();
        pose(e.caste, Mth.lerp(pt, e.bipedAmtO, e.bipedAmt), e.attack(), e.attackTick + pt, limbSwing, amt, age, headYaw, headPitch);
    }

    /** Pure pose function (also used by the offline preview tool). b = biped blend, t = ticks into attack. */
    public void pose(Caste c, float bipedBlend, Attack at, float t, float limbSwing, float amt, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        for (ModelPart p : root.getAllParts().toList()) { p.xScale = p.yScale = p.zScale = 1; p.visible = true; }
        float b = smooth(bipedBlend);
        float aw = 0;                                   // attack weight 0..1
        if (at != Attack.NONE) aw = smooth(t / 3f) * smooth((at.duration - t) / 4f);

        Sp sp = spec(c);
        tubeL.visible = tubeR.visible = c.tubes && sp.tubeLen > 0;
        arm2[0].visible = arm2[1].visible = c.extraArms;
        for (int i = 0; i < TAIL; i++) tail[i].visible = i < Math.max(1, sp.tailSegs);
        neck.yScale = sp.neckK;
        tail0.visible = sp.tailSegs > 0;

        // ---- stance
        P = Mth.lerp(b, 1.38f, 0.5f - sp.upright * b);
        float H = Mth.lerp(b, 10.5f, 13.6f) * Math.min(1.4f, 1 + (spec(c).legK - 1) * 1.45f);
        float spd = Mth.clamp(amt * 1.6f, 0, 1);
        float wt = c.weight(), fer = c.ferocity();
        float ph = limbSwing * 0.82f / (float) Math.pow(Math.max(0.6f, wt), 0.35);
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
        float hRoll = 0, flip = 0, a2w = 0;
        float[] a2p = new float[2];

        float breathe = Mth.sin(age * 0.09f);
        float sway = Mth.sin(age * 0.045f);

        // ---------- idle / locomotion into base pose
        chestX += breathe * 0.025f; neckX += breathe * -0.02f + sway * 0.02f;
        neckY += sway * 0.12f * (1 - spd); headY += Mth.sin(age * 0.031f + 1) * 0.18f * (1 - spd);
        jawOpen += 0.06f + Mth.sin(age * 0.2f) * 0.03f * (1 - spd) + (Mth.sin(age * 0.013f) > 0.97f ? 0.4f : 0);
        innerExt += Math.max(0, Mth.sin(age * 0.07f + 2) - 0.8f) * 6f;      // occasional inner-jaw flick
        float bob = -Mth.abs(Mth.sin(ph)) * 0.9f * spd * (0.4f + b * 0.6f) * wt;
        // spine undulation
        float und = Mth.sin(ph) * 0.22f * spd * (1.2f - 0.6f * b);
        bodyY += und; chestY -= und * 1.3f; neckY += und * 0.6f; headY -= und * 0.5f;
        float walkRoll = Mth.sin(ph) * 0.05f * spd;
        // tail
        for (int i = 0; i < TAIL; i++) {
            float w = (0.14f + 0.12f * spd) * (0.6f + i * 0.14f);
            tailYaw[i] = Mth.sin(age * 0.05f - i * 0.55f + ph * 0.0f) * 0.08f * (0.6f + i * 0.1f) + Mth.sin(ph * 1.0f - i * 0.7f) * w * spd;
            tailPitch[i] = 0.012f * i + Mth.sin(ph * 2 - i * 0.6f) * 0.05f * spd;
        }
        tailBaseX = Mth.lerp(b, -0.08f, -0.2f);   // biped: tail arches higher for balance, quad: trails

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
                    // wind-up: tail rises and arches over the back, draws back, then snaps forward over the shoulder
                    float curl = kf(t, 0, 0.25f, 6, 0.6f, 9, 0.5f, 11, 1.12f, 13, 1.05f, 17, 0.9f, 22, 0);
                    for (int i = 0; i < TAIL; i++) {
                        tailPitch[i] = -curl * 0.31f;
                        tailYaw[i] = tailYaw[i] * (1 - aw);
                    }
                    tailBaseX = -0.3f - 0.3f * curl;
                    hipZ += kf(t, 0, 0, 7, 1.5f, 11, -1.5f, 22, 0);
                    crouch += kf(t, 0, 0, 7, 1.5f, 11, 0, 22, 0);
                    chestX += kf(t, 0, 0, 7, -0.25f, 11, 0.3f, 22, 0);
                    headX += kf(t, 0, 0, 7, -0.2f, 11, 0.2f, 22, 0);
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
                    headX += kf(t, 0, 0, 5, -0.6f, 8, 0.2f, 11, 0.2f, 18, 0);
                    neckX += kf(t, 0, 0, 5, -0.3f, 8, 0.15f, 11, 0.1f, 18, 0);
                    chestX += kf(t, 0, 0, 5, -0.25f, 8, 0.3f, 18, 0);
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
                case BLADE_FLURRY -> {
                    float w8 = t * 0.785f;
                    rootSpin = kf(t, 0, 0, 5, 0.3f, 8, 0.9f, 30, 6.28f, 34, 6.28f);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1, ps = Mth.sin(w8 - s * Mth.PI), pc = Mth.cos(w8 - s * Mth.PI);
                        A[s][0] = -1.35f + 0.85f * ps; A[s][1] = sg * (0.5f + 0.9f * pc); A[s][2] = sg * 0.6f * ps; A[s][3] = 0.35f; A[s][4] = 0.4f * ps; A[s][5] = aw;
                    }
                    chestY += 0.5f * Mth.sin(w8) * aw; headY -= 0.4f * Mth.sin(w8) * aw; crouch += 1.4f * aw; jawOpen += 0.9f * aw;
                    for (int i = 0; i < TAIL; i++) tailYaw[i] += Mth.sin(t * 0.9f - i * 0.6f) * 0.5f * aw;
                }
                case FRENZY -> {
                    float w4 = t * 1.57f, bite = Math.max(0, Mth.sin(w4 * 0.5f)) * aw;
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1, sw = Mth.sin(w4 - s * Mth.PI);
                        A[s][0] = -1.6f + 0.8f * sw; A[s][1] = sg * (0.1f + 0.8f * Mth.cos(w4 - s * Mth.PI)); A[s][2] = sg * 0.4f * sw; A[s][3] = 0.4f + 0.3f * sw; A[s][5] = aw;
                    }
                    headX += 0.5f * bite - 0.2f * aw; neckX += 0.4f * bite; chestX += 0.3f * bite; jawOpen += 1.0f * bite + 0.2f * aw; innerExt += 7 * Math.max(0, Mth.sin(w4 * 0.5f + 0.5f)) * aw;
                    crouch += (1.5f + Mth.sin(w4 * 0.5f) * 0.8f) * aw; chestY += 0.4f * Mth.sin(w4) * aw; headY += 0.3f * Mth.cos(w4) * aw;
                    for (int i = 0; i < TAIL; i++) tailYaw[i] += Mth.sin(t * 0.8f - i * 0.5f) * 0.4f * aw;
                }
                case HORN_TOSS -> {
                    headX += kf(t, 0, 0, 6, 0.95f, 12, 1.0f, 17, -0.9f, 24, -0.3f, 30, 0);
                    neckX += kf(t, 0, 0, 6, 0.6f, 12, 0.7f, 17, -0.7f, 30, 0);
                    chestX += kf(t, 0, 0, 6, 0.35f, 12, 0.4f, 17, -0.5f, 30, 0);
                    crouch += kf(t, 0, 0, 6, 3f, 13, 3.5f, 17, -0.5f, 30, 0);
                    hipZ += kf(t, 0, 0, 6, 1.5f, 13, -1f, 30, 0);
                    jawOpen += 0.1f;
                    for (int s = 0; s < 2; s++) { float sg = s == 0 ? 1 : -1; A[s][0] = kf(t, 0, -0.6f, 6, -0.9f, 17, -1.8f, 30, -0.6f); A[s][1] = sg * kf(t, 0, -0.2f, 6, -0.7f, 17, -0.9f, 30, -0.2f); A[s][3] = 0.9f; A[s][5] = aw * 0.8f; }
                    for (int i = 0; i < TAIL; i++) tailPitch[i] = -0.08f * i * aw;
                }
                case STOMP -> {
                    float rise = kf(t, 0, 0, 10, 1, 15, 1, 17, 0);
                    P = Mth.lerp(rise * 0.8f, P, 0.05f);
                    crouch += kf(t, 0, 0, 6, -0.5f, 12, -3.5f, 15, -3f, 17, 4.5f, 22, 3f, 38, 0);
                    chestX += kf(t, 0, 0, 12, -0.55f, 15, -0.5f, 17, 0.7f, 24, 0.3f, 38, 0);
                    headX += kf(t, 0, 0, 12, -0.8f, 15, -0.8f, 17, 0.6f, 38, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 1.2f, 15, 1.45f, 17, 0.3f, 38, 0.1f);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 12, -2.9f, 15, -2.9f, 17, -0.7f, 38, -0.6f); A[s][1] = sg * kf(t, 0, -0.2f, 12, -0.9f, 15, -0.9f, 17, -0.1f, 38, -0.2f);
                        A[s][2] = sg * kf(t, 0, 0, 12, -0.5f, 17, 0, 38, 0); A[s][3] = kf(t, 0, 0.9f, 12, 0.3f, 17, 0.2f, 38, 0.9f); A[s][5] = aw;
                    }
                    float qk = Mth.sin(t * 5f) * kf(t, 17, 1, 30, 0); bodyX += qk * 0.05f; chestZ += qk * 0.04f;
                    for (int i = 0; i < TAIL; i++) tailPitch[i] = kf(t, 0, tailPitch[i], 12, -0.07f * i, 17, 0.04f, 38, tailPitch[i]);
                }
                case GRAB_SLAM -> {
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 5, -1.7f, 8, -1.65f, 14, -2.9f, 28, -3.0f, 30, -0.9f, 33, -0.9f, 40, -2.9f, 44, -3.0f, 46, -0.8f, 50, -1.5f, 58, -0.6f);
                        A[s][1] = sg * kf(t, 0, -0.2f, 8, -0.4f, 14, 0.1f, 44, 0.1f, 46, -0.2f, 58, -0.2f); A[s][2] = sg * kf(t, 0, 0, 14, -0.2f, 58, 0);
                        A[s][3] = kf(t, 0, 0.9f, 8, 0.15f, 14, 0.4f, 28, 0.3f, 30, 0.15f, 40, 0.4f, 46, 0.2f, 58, 0.9f); A[s][4] = 0.3f; A[s][5] = aw;
                    }
                    chestX += kf(t, 0, 0, 8, 0.2f, 14, -0.45f, 28, -0.5f, 30, 0.8f, 33, 0.6f, 40, -0.5f, 44, -0.5f, 46, 0.85f, 52, 0.2f, 58, 0);
                    headX += kf(t, 0, 0, 14, -0.4f, 28, -0.5f, 30, 0.7f, 40, -0.5f, 46, 0.7f, 58, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 1f, 14, 1.3f, 30, 0.5f, 40, 1.3f, 46, 0.5f, 58, 0.1f);
                    crouch += kf(t, 0, 0, 8, 1f, 28, -0.5f, 30, 3.5f, 34, 1.5f, 44, -0.5f, 46, 4f, 52, 1f, 58, 0);
                    for (int i = 0; i < TAIL; i++) tailPitch[i] -= 0.04f * i * aw;
                }
                case SHADOW_STRIKE -> {
                    crouch += kf(t, 0, 0, 5, 5.5f, 12, 5.5f, 13, -0.5f, 18, 2f, 28, 0);
                    P = Mth.lerp(kf(t, 2, 0, 6, 1, 12, 1, 15, 0), P, 1.45f);
                    chestX += kf(t, 0, 0, 6, 0.3f, 12, 0.3f, 14, -0.3f, 28, 0); headX += kf(t, 0, 0, 6, 0.4f, 12, 0.4f, 14, -0.5f, 28, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1, d = s * 2;
                        A[s][0] = kf(t - d, 0, -0.6f, 6, -0.2f, 12, 0.3f, 14, -2.2f, 17, -1.3f, 28, -0.6f); A[s][1] = sg * kf(t - d, 0, -0.2f, 12, -0.9f, 14, 1.0f, 17, 0.7f, 28, -0.2f); A[s][3] = 0.6f; A[s][5] = aw;
                    }
                    jawOpen += kf(t, 0, 0, 12, 0, 14, 1.2f, 20, 0.2f, 28, 0);
                    for (int i = 0; i < TAIL; i++) tailPitch[i] = kf(t, 0, tailPitch[i], 6, -0.12f * i, 12, -0.12f * i, 14, 0.03f * i, 28, tailPitch[i]);
                }
                case IMPREGNATE -> {
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 5, -1.75f, 8, -1.65f, 12, -1.6f, 20, -1.9f, 56, -1.9f, 60, -1.0f, 64, -0.6f); A[s][1] = sg * kf(t, 0, -0.2f, 8, -0.45f, 20, 0.4f, 56, 0.4f, 64, -0.2f);
                        A[s][3] = kf(t, 0, 0.9f, 8, 0.15f, 20, 1.2f, 56, 1.2f, 64, 0.9f); A[s][5] = aw;
                    }
                    chestX += kf(t, 0, 0, 8, 0.2f, 20, 0.45f, 52, 0.4f, 64, 0); headX += kf(t, 0, 0, 20, 0.5f, 52, 0.5f, 64, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 1.0f, 28, 1.3f, 52, 1.2f, 64, 0.1f); innerExt += kf(t, 28, 0, 34, 9, 40, 9, 46, 0);
                    crouch += kf(t, 0, 0, 8, 1.2f, 56, 1.2f, 64, 0);
                    float tl = kf(t, 0, 0, 14, 0, 26, 1, 34, 1.15f, 46, 1, 56, 0, 64, 0);       // tail curls forward under the body
                    tailBaseX = Mth.lerp(tl, tailBaseX, 1.25f);
                    for (int i = 0; i < TAIL; i++) { tailPitch[i] = Mth.lerp(tl, tailPitch[i], 0.22f); tailYaw[i] *= (1 - tl); }
                }
                case DEATH_ROLL -> {
                    hRoll = kf(t, 8, 0, 50, 18.85f);
                    float rl = kf(t, 8, 0, 12, 1, 48, 1, 52, 0);
                    jawOpen += 1.2f * aw; crouch += 1.2f * aw; headZ += Mth.sin(t * 1.5f) * 0.2f * rl; headY += Mth.sin(t * 1.8f) * 0.4f * rl;
                    for (int s = 0; s < 2; s++) { A[s][0] = kf(t, 0, -0.6f, 6, -1.7f, 10, -1.0f, 50, -1.0f, 56, -0.6f); A[s][1] = (s == 0 ? 1 : -1) * 0.5f; A[s][3] = 1.3f; A[s][5] = aw; }
                    for (int i = 0; i < TAIL; i++) tailYaw[i] += Mth.sin(t * 0.9f - i * 0.5f) * 0.8f * rl;
                }
                case LEAP_SLASH -> {
                    float air = kf(t, 7, 0, 9, 1, 22, 1, 25, 0);
                    flip = kf(t, 8, 0, 21, 6.28f); legOv[0] = air; legOv[1] = 2f; legOv[2] = 7f;
                    crouch += kf(t, 0, 0, 6, 4f, 8, -1f, 22, -1f, 25, 3f, 30, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 1.0f, 20, 1.0f, 26, 0.1f);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 6, 0.3f, 8, -2.3f, 13, -2.3f, 15, -0.9f, 26, -0.6f); A[s][1] = sg * kf(t, 0, -0.2f, 6, -0.6f, 8, -1.4f, 12, -1.4f, 15, 0.9f, 26, -0.2f);
                        A[s][3] = 0.4f; A[s][5] = aw;
                    }
                }
                case FLAIL -> {
                    float ww = t * 0.8f;
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = ww * (s == 0 ? 1 : -0.8f) - 1.0f; A[s][1] = sg * (0.3f + 0.5f * Mth.sin(ww * 1.3f + s)); A[s][2] = sg * 0.8f * Mth.sin(ww + s);
                        A[s][3] = 0.3f + 0.4f * Mth.sin(ww * 2); A[s][5] = aw;
                    }
                    headY += 0.8f * Mth.sin(t * 1.1f) * aw; headX += 0.3f * Mth.sin(t * 1.7f) * aw; chestY += 0.6f * Mth.sin(t * 0.9f) * aw; chestX += 0.2f * Mth.sin(t * 1.3f) * aw;
                    crouch += (1.0f + 0.8f * Mth.sin(t * 1.5f)) * aw; jawOpen += 1.2f * aw;
                    for (int i = 0; i < TAIL; i++) tailYaw[i] += Mth.sin(t * 1.2f - i * 0.5f) * 0.7f * aw;
                }
                case SPORE -> {
                    float sw = kf(t, 0, 0, 18, 1, 20, 1.15f, 26, 0.1f, 44, 0);
                    chestScale = 1 + 0.4f * sw; neckScale = 1 + 0.2f * sw;
                    headX += kf(t, 0, 0, 16, -0.8f, 20, -0.9f, 26, 0.2f, 44, 0); chestX += kf(t, 0, 0, 16, -0.4f, 20, -0.45f, 26, 0.1f, 44, 0);
                    jawOpen += kf(t, 0, 0.1f, 16, 1.2f, 26, 1.3f, 34, 0.2f, 44, 0.1f); crouch += kf(t, 0, 0, 18, 1.2f, 20, -0.5f, 30, 0.5f, 44, 0);
                    chestZ += Mth.sin(t * 3) * 0.04f * kf(t, 8, 0, 18, 1, 20, 0);
                    for (int s = 0; s < 2; s++) { float sg = s == 0 ? 1 : -1; A[s][0] = -0.9f; A[s][1] = sg * (-0.2f - 0.9f * sw); A[s][2] = sg * -0.5f * sw; A[s][3] = 0.5f; A[s][5] = aw * Math.min(1, sw * 2); }
                }
                case LOB -> {
                    headX += kf(t, 0, 0, 9, -1.0f, 12, -0.9f, 14, 0.7f, 22, 0.2f, 30, 0); neckX += kf(t, 0, 0, 9, -0.6f, 13, 0.5f, 30, 0);
                    neckScale = 1 + 0.5f * kf(t, 0, 0, 9, 1, 13, 0, 22, 0); chestX += kf(t, 0, 0, 9, -0.6f, 14, 0.4f, 30, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 0.7f, 13, 1.4f, 18, 0.3f, 30, 0.1f); crouch += kf(t, 0, 0, 9, -0.5f, 14, 2f, 30, 0);
                    A[0][0] = kf(t, 0, -0.6f, 9, -2.8f, 14, -0.4f, 30, -0.6f); A[0][1] = 0.4f; A[0][3] = 0.5f; A[0][5] = aw;
                    A[1][0] = -1.0f; A[1][1] = -0.4f; A[1][3] = 1.2f; A[1][5] = aw * 0.8f;
                }
                case VOLLEY -> {
                    float v = Math.max(0, Mth.sin((t - 8f) * 1.25f)) * kf(t, 8, 0, 9, 1, 25, 1, 27, 0);
                    headX += -0.5f * v - 0.2f * aw; jawOpen += 0.1f + 1.3f * v; neckScale = 1 + 0.35f * v; chestScale = 1 + 0.07f * v; chestX += -0.2f * v; crouch += 1.2f * aw;
                    for (int s = 0; s < 2; s++) { A[s][0] = -1.0f; A[s][1] = (s == 0 ? 1 : -1) * 0.4f; A[s][3] = 1.2f; A[s][5] = aw * 0.8f; }
                }
                case VOMIT -> {
                    float up = kf(t, 0, 0, 8, 1, 36, 1, 42, 0), pulse = Mth.sin(t * 0.9f) * kf(t, 10, 0, 14, 1, 34, 1, 38, 0);
                    headX += -1.1f * up; neckX += -0.5f * up; chestX += -0.5f * up + pulse * 0.05f; jawOpen += 0.1f + 1.4f * up; innerExt += 4 * up;
                    chestScale = 1 + 0.12f * pulse; neckScale = 1 + 0.4f * up + 0.2f * pulse; crouch += 1.2f * up;
                    for (int s = 0; s < 2; s++) { A[s][0] = -0.9f; A[s][1] = (s == 0 ? 1 : -1) * 0.6f; A[s][3] = 0.8f; A[s][5] = aw * 0.8f; }
                }
                case DETONATE -> {
                    float sw = kf(t, 0, 0, 34, 1, 36, 1), shake = Mth.sin(t * 2.9f) * sw * sw;
                    chestScale = 1 + 0.9f * sw; neckScale = 1 + 0.4f * sw; bodyY += shake * 0.12f; chestZ += shake * 0.08f; headZ += shake * 0.2f; jawOpen += 0.1f + 1.4f * sw;
                    crouch += 1.5f * sw + shake * 0.6f; innerExt += 3 * sw;
                    for (int s = 0; s < 2; s++) { A[s][0] = -1.0f + shake * 0.5f; A[s][1] = (s == 0 ? 1 : -1) * 0.7f; A[s][3] = 0.5f; A[s][5] = aw; }
                }
                case SUMMON -> {
                    float up = kf(t, 0, 0, 10, 1, 50, 1, 58, 0);
                    P = Mth.lerp(up * b, P, 0.1f);
                    chestX += -0.55f * up; headX += -0.9f * up; neckX += -0.5f * up; jawOpen += 0.1f + 1.4f * up * (0.6f + 0.4f * Mth.sin(t * 0.9f)); innerExt += 5 * up;
                    crouch += 1.5f * up * Math.max(0, Mth.sin(t * 0.55f)); chestScale = 1 + 0.1f * up * Math.max(0, Mth.sin(t * 0.55f));
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 10, -1.5f, 50, -1.5f, 58, -0.6f); A[s][1] = sg * (kf(t, 0, -0.2f, 10, -1.7f, 50, -1.7f, 58, -0.2f) + Mth.sin(t * 0.4f) * 0.15f * up);
                        A[s][2] = sg * -0.9f * up; A[s][3] = 0.3f; A[s][5] = aw;
                    }
                    for (int i = 0; i < TAIL; i++) { tailPitch[i] = -0.09f * i * up + Mth.sin(t * 1.2f - i) * 0.05f * up; tailYaw[i] *= (1 - up); }
                }
                case FACE_TOSS -> {
                    chestScale = 1 + 0.15f * kf(t, 0, 0, 8, 1, 24, 1, 30, 0);
                    chestX += kf(t, 0, 0, 11, -0.55f, 14, 0.5f, 19, -0.55f, 22, 0.5f, 32, 0); headX += kf(t, 0, 0, 11, -0.3f, 14, 0.4f, 19, -0.3f, 22, 0.4f, 32, 0);
                    jawOpen += kf(t, 0, 0.1f, 11, 1f, 14, 0.4f, 19, 1f, 22, 0.4f, 32, 0.1f); crouch += kf(t, 0, 0, 8, 1.5f, 24, 1.5f, 32, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 5, -1.9f, 11, -3.0f, 14, -0.5f, 17, -1.7f, 19, -3.0f, 22, -0.5f, 32, -0.6f); A[s][1] = sg * 0.3f; A[s][3] = 0.4f; A[s][5] = aw;
                    }
                }
                case WEB -> {
                    headX += kf(t, 0, 0, 8, -0.5f, 11, 0.5f, 18, 0, 26, 0); neckScale = 1 + 0.4f * kf(t, 0, 0, 8, 1, 11, 0, 26, 0); jawOpen += kf(t, 0, 0.1f, 7, 0.9f, 12, 1.2f, 26, 0.1f);
                    float rl = kf(t, 10, 0, 12, 1, 24, 1, 26, 0); a2w = aw;
                    for (int s = 0; s < 2; s++) {
                        float rs = Mth.sin((t - 11) * 0.9f - s * Mth.PI);
                        A[s][0] = -1.6f + 0.9f * rs * rl; A[s][1] = (s == 0 ? 1 : -1) * 0.3f; A[s][3] = 0.8f; A[s][5] = aw; a2p[s] = -1.2f + 0.8f * rs * rl;
                    }
                    crouch += 1.5f * aw;
                }
                case MULTI_STAB -> {
                    a2w = aw;
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1, st0 = Math.max(0, Mth.sin((t - 6) * 1.05f - s * Mth.PI)) * kf(t, 6, 0, 8, 1, 26, 1, 30, 0);
                        float st2 = Math.max(0, Mth.sin((t - 6) * 1.05f - s * Mth.PI + 1.57f)) * kf(t, 6, 0, 8, 1, 26, 1, 30, 0);
                        A[s][0] = -1.0f - 0.7f * st0; A[s][1] = sg * (0.3f - 0.3f * st0); A[s][3] = 1.2f - 1.0f * st0; A[s][5] = aw; a2p[s] = -0.7f - 0.9f * st2;
                    }
                    chestX += 0.1f * Mth.sin(t * 1.05f) * aw; crouch += aw; jawOpen += 0.6f * aw;
                }
                case CREST_RAM -> {
                    headX += kf(t, 0, 0, 5, -0.7f, 8, -0.8f, 11, 0.55f, 18, 0.3f, 26, 0); neckX += kf(t, 0, 0, 8, -0.5f, 11, 0.5f, 26, 0);
                    chestX += kf(t, 0, 0, 8, -0.35f, 11, 0.5f, 18, 0.2f, 26, 0); crouch += kf(t, 0, 0, 8, 2f, 11, -0.3f, 26, 0); hipZ += kf(t, 0, 0, 8, 1.6f, 11, -2.2f, 26, 0);
                    for (int s = 0; s < 2; s++) { float sg = s == 0 ? 1 : -1; A[s][0] = kf(t, 0, -0.6f, 8, -0.8f, 26, -0.6f); A[s][1] = sg * kf(t, 0, -0.2f, 8, -0.7f, 11, -0.4f, 26, -0.2f); A[s][3] = 0.9f; A[s][5] = aw * 0.8f; }
                }
                default -> {}
            }
            // heavy bodies: impact squash, quake
            if (wt > 1.2f) {
                float imp = kf(t, Math.max(0, hit - 1), 0, hit, 1, hit + 8, 0);
                crouch += imp * (wt - 1) * 1.6f; chestX += imp * 0.12f * (wt - 1); bodyX += Mth.sin(t * 4.5f) * imp * 0.05f * (wt - 1);
            }
        }
        // temper: twitchy, hunched idle
        headY += Mth.sin(age * 0.8f) * 0.07f * fer * (1 - aw); jawOpen += fer * 0.25f * (1 - aw); crouch += 0.5f * fer * (1 - aw);
        if (false) {
            switch (at) {
                default -> {}
            }
        }

        // ---------- apply stance/pose to parts
        float hAct = H - crouch;
        hips.y = 24 - hAct + bob;
        hips.z = 1 + hipZ + (1 - b) * -1.5f;
        hips.zRot = walkRoll + hRoll; hips.xRot = flip;
        // quad body is long: shorten "bipedal" crouch effect on hips height etc handled by IK
        body.xRot = P + bodyX; body.yRot = bodyY; body.zRot = walkRoll * -1.5f;
        float aim = P - Mth.lerp(b, 0.15f, 0.55f);                    // how much the head must tilt back up
        chest.xRot = chestX - aim * 0.22f; chest.yRot = chestY; chest.zRot = chestZ;
        neck.xRot = neckX - aim * 0.42f; neck.yRot = neckY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.35f;
        head.xRot = headX - aim * 0.34f - 0.22f * b + Mth.clamp(headPitch, -45, 45) * Mth.DEG_TO_RAD * 0.7f;
        head.yRot = headY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.3f; head.zRot = headZ;
        jaw.xRot = Mth.clamp(jawOpen, 0, 1.5f);
        inner.z = -1.5f - innerExt; inner.xRot = jaw.xRot * 0.15f;
        chest.xScale = chestScale; chest.yScale = chestScale; chest.zScale = chestScale;
        neck.xScale = neck.zScale = neckScale;
        root.yRot = rootSpin;
        tubeL.xRot = 0.25f + breathe * 0.03f - und * 0.2f; tubeR.xRot = tubeL.xRot;
        tubeL.yRot = 0.1f + und * 0.3f; tubeR.yRot = -tubeL.yRot;

        // extra Queen arms flutter
        for (int s = 0; s < 2; s++) {
            float sg = s == 0 ? 1 : -1;
            arm2[s].xRot = Mth.lerp(a2w, -0.9f + Mth.sin(age * 0.07f + s * 2) * 0.12f + (at == Attack.NONE ? 0 : -0.6f * aw), a2p[s]);
            arm2[s].zRot = sg * -0.25f; fore2[s].xRot = -1.0f + Mth.sin(age * 0.09f + s) * 0.15f;
        }

        // tail
        // authored with negative = raise; model +xRot lifts the tip, so flip here
        tail0.xRot = -(tailBaseX + tailPitch[0]); tail0.yRot = tailYaw[0];
        for (int i = 1; i < TAIL; i++) { tail[i].xRot = -tailPitch[i]; tail[i].yRot = tailYaw[i]; }

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
            float lk = sp.legK;
            float[] a = ik(tz, ty, 8f * lk, 8f * lk, true);
            thigh[s].yScale = lk;
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
            float[] q = ik(wz - shoulderZ, Math.max(1f, hAct + shoulderUp - 1.6f - lift), 8.5f * sp.armK, 10f * sp.armK, false);
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
            if (c == Caste.PATHOGEN_BRUTE) { hand[s].xScale = hand[s].yScale = hand[s].zScale = 1.9f; fore[s].xScale = fore[s].zScale = 1.25f; }
            arm[s].yScale = sp.armK;
            arm[s].xRot = pitch - body.xRot - chest.xRot; arm[s].yRot = yaw; arm[s].zRot = roll;
            fore[s].xRot = el; hand[s].xRot = hnd;
        }
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float bl, float a) {
        root.render(ps, vc, light, overlay, r, g, bl, a);
    }
}
