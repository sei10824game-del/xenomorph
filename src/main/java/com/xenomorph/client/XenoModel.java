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
    ModelPart ovi;   // queen ovipositor, only on castes that lay eggs
    ModelPart[] shields, vines, xarms, sacs, crystals, lids, tusks;   // mutant-lineage extras (null when the caste has none)
    ModelPart cape;
    float tintR = 1, tintG = 1, tintB = 1;   // per-individual / per-status colour multiplier
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
        if (hips.hasChild("ovi")) ovi = hips.getChild("ovi");
        if (chest.hasChild("shield0")) shields = new ModelPart[]{chest.getChild("shield0"), chest.getChild("shield1"), chest.getChild("shield2")};
        if (chest.hasChild("vineL")) vines = new ModelPart[]{chest.getChild("vineL"), chest.getChild("vineR")};
        if (chest.hasChild("xarmL")) xarms = new ModelPart[]{chest.getChild("xarmL"), chest.getChild("xarmR")};
        if (chest.hasChild("sacL")) sacs = new ModelPart[]{chest.getChild("sacL"), chest.getChild("sacR")};
        if (chest.hasChild("lidL")) lids = new ModelPart[]{chest.getChild("lidL"), chest.getChild("lidR")};
        if (head.hasChild("tuskL")) tusks = new ModelPart[]{head.getChild("tuskL"), head.getChild("tuskR")};
        if (chest.hasChild("crystal0")) { crystals = new ModelPart[7]; for (int i = 0; i < 7; i++) crystals[i] = chest.getChild("crystal" + i); }
        if (chest.hasChild("cape")) cape = chest.getChild("cape");
        tail0 = hips.getChild("tail0");
        ModelPart p = tail0;
        for (int i = 0; i < TAIL; i++) { tail[i] = i == 0 ? tail0 : p.getChild("tail" + i); p = tail[i]; }
    }

    // ------------------------------------------------------------------ geometry
    private static int domeCu;   // the cranium owns a reserved texture strip (y 72..96) so a glossy highlight layer can target it
    private static CubeListBuilder dome(CubeListBuilder b, float x, float y, float z, float w, float h, float d) {
        int bw = (int) Math.ceil(2 * (w + d)) + 1;
        if (domeCu + bw > 256) domeCu = 0;
        b.texOffs(domeCu, 72).addBox(x, y, z, w, h, d); domeCu += bw; return b;
    }
    private static int cu, cv, rowH;
    private static CubeListBuilder box(float x, float y, float z, float w, float h, float d) {
        int bw = (int) Math.ceil(2 * (w + d)) + 1, bh = (int) Math.ceil(h + d) + 1;
        if (cu + bw > 256) { cu = 0; cv += rowH; rowH = 0; }
        if (cv + bh > 72) { cu = 0; cv = 0; rowH = 0; }
        CubeListBuilder b = CubeListBuilder.create().texOffs(cu, cv).addBox(x, y, z, w, h, d);
        cu += bw; rowH = Math.max(rowH, bh);
        return b;
    }
    private static CubeListBuilder add(CubeListBuilder b, float x, float y, float z, float w, float h, float d) {
        int bw = (int) Math.ceil(2 * (w + d)) + 1, bh = (int) Math.ceil(h + d) + 1;
        if (cu + bw > 256) { cu = 0; cv += rowH; rowH = 0; }
        if (cv + bh > 72) { cu = 0; cv = 0; rowH = 0; }
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
        float domeL = 15, domeH = 3.6f, domeW = 4.0f, headW = 1, torso = 1, limb = 1, tailT = 1, clawL = 1, fan = 12, armK = 1, legK = 1, neckK = 1, upright = 0;
        int style, tailSegs = 8, tailEnd, ribs = 3, spines, plates, horns, tubeLen = 14;
        boolean singleNail, glowHead, rider, wings, fan2, bare, cheek, mand, carapace, sac, belly, knee, blade, elbow, dreads, brow;
    }
    // style: 0 smooth dome, 1 ridged, 2 crowned fan, 3 frill+horns, 4 bulb (pathogen/neomorph), 5 spiked crest
    // tailEnd: 0 blade, 1 spear, 2 club, 3 twin-blade, 4 bare tip
    static Sp spec(Caste c) {
        Sp p = new Sp();
        switch (c) {
            case HULK_XENO -> { p.style = 5; p.domeL = 9; p.domeW = 6.4f; p.domeH = 5.6f; p.torso = 1.55f; p.limb = 1.45f; p.armK = 1.35f; p.plates = 2; p.spines = 9; p.ribs = 3; p.elbow = true; p.knee = true; p.carapace = true; p.tailEnd = 2; p.tailT = 1.2f; p.cheek = true; p.brow = true; p.clawL = 1.2f; }   // Mutant Zombie: hulking slab of muscle, giant fists
            case BONE_XENO -> { p.domeL = 14; p.domeW = 3.4f; p.limb = 0.78f; p.torso = 0.9f; p.armK = 1.3f; p.legK = 1.15f; p.neckK = 1.2f; p.ribs = 9; p.spines = 7; p.tubeLen = 0; p.elbow = true; p.knee = true; p.mand = true; p.clawL = 1.5f; p.tailEnd = 1; p.bare = true; }   // Mutant Skeleton: exposed rib cage and long bone limbs
            case BLAST_XENO -> { p.style = 4; p.sac = true; p.domeW = 5.6f; p.domeH = 4.8f; p.torso = 1.25f; p.limb = 1.1f; p.spines = 5; p.ribs = 2; p.plates = 1; p.cheek = true; p.tubeLen = 0; p.tailEnd = 4; p.clawL = 1.1f; }   // Mutant Creeper: swollen, charged body
            case VOID_XENO -> { p.style = 2; p.fan = 13; p.domeL = 16; p.domeH = 4.4f; p.limb = 0.92f; p.torso = 1.05f; p.armK = 1.5f; p.legK = 1.35f; p.neckK = 1.3f; p.upright = 0.12f; p.dreads = true; p.mand = true; p.ribs = 5; p.spines = 4; p.tubeLen = 0; p.clawL = 1.8f; p.tailEnd = 3; p.horns = 2; }   // Mutant Enderman: tall, long-armed, extra limbs trailing
            case FROST_XENO -> { p.style = 3; p.horns = 4; p.domeL = 10; p.domeW = 5f; p.carapace = true; p.plates = 2; p.spines = 8; p.ribs = 2; p.torso = 1.3f; p.limb = 1.2f; p.armK = 1.15f; p.tubeLen = 6; p.tailEnd = 2; p.clawL = 1.2f; p.cheek = true; }   // Mutant Snow Golem: iced carapace, crystal spikes
            case WITHER_XENO -> { p.domeL = 15; p.domeW = 3.8f; p.limb = 0.9f; p.torso = 0.95f; p.armK = 1.45f; p.legK = 1.2f; p.neckK = 1.15f; p.ribs = 7; p.spines = 6; p.blade = true; p.elbow = true; p.mand = true; p.clawL = 1.7f; p.tailEnd = 3; p.brow = true; }   // Mutant Wither Skeleton: lean, long blade arms
            case BLAZE_XENO -> { p.style = 5; p.sac = true; p.domeL = 11; p.domeW = 5.4f; p.torso = 1.3f; p.limb = 1.15f; p.plates = 2; p.spines = 8; p.carapace = true; p.cheek = true; p.ribs = 2; p.tailEnd = 4; p.horns = 3; p.clawL = 1.2f; }   // Mutant Blaze: layered armour plates (the shields)
            case SHULKER_XENO -> { p.style = 3; p.domeL = 8; p.domeW = 6f; p.domeH = 5f; p.torso = 1.5f; p.limb = 1.35f; p.armK = 0.9f; p.legK = 0.85f; p.neckK = 0.8f; p.plates = 2; p.carapace = true; p.spines = 4; p.ribs = 0; p.elbow = true; p.knee = true; p.tailEnd = 2; p.tailSegs = 6; }   // Mutant Shulker: shell-backed
            case HUSK_XENO -> { p.style = 5; p.domeL = 9; p.domeW = 6.2f; p.domeH = 5.4f; p.torso = 1.5f; p.limb = 1.4f; p.armK = 1.3f; p.plates = 2; p.spines = 7; p.ribs = 3; p.elbow = true; p.knee = true; p.carapace = true; p.tailEnd = 2; p.tailT = 1.15f; p.cheek = true; p.clawL = 1.15f; }   // Mutant Husk: bulky hammer fists
            case HOG_XENO -> { p.style = 3; p.horns = 6; p.domeL = 7; p.domeW = 6.8f; p.domeH = 5.6f; p.torso = 1.45f; p.limb = 1.3f; p.neckK = 0.6f; p.plates = 1; p.spines = 9; p.ribs = 3; p.mand = true; p.cheek = true; p.carapace = true; p.tailEnd = 2; p.tailSegs = 5; p.clawL = 1.1f; }   // Mutant Hoglin: tusked, mane of spines
            case FROZEN_XENO -> { p.style = 3; p.horns = 5; p.domeL = 9; p.domeW = 5.6f; p.torso = 1.4f; p.limb = 1.3f; p.armK = 1.05f; p.plates = 2; p.carapace = true; p.spines = 11; p.ribs = 2; p.elbow = true; p.knee = true; p.tailEnd = 2; p.cheek = true; p.clawL = 1.1f; }   // Mutant Frozen Zombie: icicle-spiked back
            case JUNGLE_XENO -> { p.style = 2; p.fan = 11; p.domeL = 12; p.domeW = 4.4f; p.torso = 1.25f; p.limb = 1.2f; p.armK = 1.4f; p.dreads = true; p.mand = true; p.spines = 6; p.ribs = 4; p.tailEnd = 3; p.tailSegs = 10; p.clawL = 1.5f; p.elbow = true; p.horns = 3; }   // Mutant Jungle Zombie: vine-draped
            case CHIMERA_QUEEN -> { p.style = 3; p.horns = 7; p.fan = 15; p.domeL = 17; p.domeH = 5.6f; p.domeW = 5.4f; p.torso = 1.35f; p.limb = 1.2f; p.armK = 1.3f; p.plates = 2; p.carapace = true; p.spines = 12; p.ribs = 6; p.elbow = true; p.knee = true; p.dreads = true; p.mand = true; p.cheek = true; p.sac = true; p.tailEnd = 3; p.tailT = 1.15f; p.clawL = 1.5f; }   // every mutant trait on one queen
            case DRONE -> { p.spines = 1; }
            case RED_DRONE -> { p.spines = 5; p.clawL = 1.4f; p.cheek = true; p.blade = true; p.ribs = 4; p.limb = 1.05f; }
            case RED_WARRIOR -> { p.style = 1; p.spines = 6; p.clawL = 1.4f; p.cheek = true; p.blade = true; p.elbow = true; p.ribs = 5; p.limb = 1.1f; }
            case K_DRONE -> { p.spines = 2; p.plates = 1; p.ribs = 4; p.elbow = true; p.knee = true; p.limb = 1.15f; p.torso = 1.1f; }
            case WARRIOR, CLONE_WARRIOR -> { p.style = 1; p.ribs = 4; p.spines = 3; p.clawL = 1.15f; p.brow = true; }
            case PRAETORIAN, K_PRAETORIAN, CLONE_PRAETORIAN -> { p.style = 2; p.fan = 13; p.limb = 1.2f; p.torso = 1.2f; p.plates = 1; p.spines = 4; p.horns = 2; p.elbow = true; p.tailEnd = 1; }
            case QUEEN, K_QUEEN, CLONE_QUEEN -> { p.style = 2; p.fan = 17; p.domeL = 14; p.torso = 1.15f; p.carapace = true; p.spines = 5; p.ribs = 4; p.plates = 1; p.horns = 3; p.limb = 1.1f; }
            case MATRIARCH -> { p.style = 2; p.fan = 14; p.domeL = 16; p.domeH = 5; p.limb = 0.9f; p.spines = 7; p.sac = true; p.horns = 2; p.tailEnd = 3; p.clawL = 1.3f; p.belly = true; }
            case EMPRESS -> { p.fan2 = true; p.style = 2; p.fan = 20; p.domeL = 13; p.carapace = true; p.plates = 2; p.horns = 5; p.tailEnd = 1; p.torso = 1.25f; p.elbow = true; p.knee = true; p.ribs = 5; }
            case QUEEN_MOTHER -> { p.style = 2; p.fan = 13; p.domeL = 18; p.domeH = 6; p.domeW = 5.6f; p.carapace = true; p.plates = 2; p.horns = 7; p.spines = 9; p.belly = true; p.elbow = true; p.knee = true; p.torso = 1.35f; p.limb = 1.25f; p.tailEnd = 3; p.cheek = true; p.mand = true; }
            case RUNNER -> { p.domeL = 9; p.domeH = 3.6f; p.domeW = 3.6f; p.limb = 0.8f; p.torso = 0.85f; p.tubeLen = 7; p.cheek = true; p.clawL = 1.2f; p.ribs = 2; p.tailEnd = 4; }
            case CRUSHER -> { p.style = 3; p.horns = 3; p.carapace = true; p.limb = 1.35f; p.torso = 1.3f; p.tailSegs = 6; p.tailEnd = 2; p.plates = 2; p.knee = true; p.domeL = 7; p.domeH = 5; p.domeW = 5; p.ribs = 1; }
            case SPITTER -> { p.domeL = 13; p.domeH = 4; p.sac = true; p.spines = 0; p.tailEnd = 4; p.tailT = 0.7f; p.limb = 0.85f; p.tubeLen = 9; p.ribs = 4; }
            case RAVAGER -> { p.style = 1; p.blade = true; p.elbow = true; p.spines = 7; p.cheek = true; p.clawL = 1.2f; p.tailEnd = 3; p.ribs = 4; p.brow = true; }
            case LURKER -> { p.domeL = 17; p.domeH = 3.4f; p.limb = 0.8f; p.clawL = 1.7f; p.torso = 0.85f; p.armK = 1.2f; p.legK = 1.1f; p.tubeLen = 0; p.ribs = 6; p.spines = 0; p.tailT = 0.8f; p.neckK = 1.2f; }
            case SENTINEL -> { p.style = 1; p.spines = 5; p.limb = 0.95f; p.clawL = 1.3f; p.tubeLen = 0; p.ribs = 4; p.plates = 1; p.tailEnd = 0; }
            case CARRIER -> { p.domeL = 12; p.limb = 1.1f; p.torso = 1.1f; p.ribs = 2; p.tubeLen = 16; p.spines = 0; }
            case PREDALIEN -> { p.dreads = true; p.domeL = 8; p.domeW = 5.4f; p.domeH = 5; p.mand = true; p.torso = 1.25f; p.plates = 1; p.spines = 3; p.ribs = 4; p.limb = 1.15f; p.cheek = true; p.brow = true; }
            case BERSERKER -> { p.style = 0; p.domeL = 7; p.domeW = 6.2f; p.domeH = 5.4f; p.brow = true; p.cheek = true; p.torso = 1.2f; p.limb = 1.15f; p.plates = 1; p.spines = 5; p.ribs = 4; p.elbow = true; p.tailEnd = 1; p.armK = 1.1f; p.clawL = 1.3f; }
            case RED_QUEEN_MOTHER -> { p.style = 2; p.fan = 21; p.domeL = 15; p.domeH = 5.2f; p.carapace = true; p.plates = 1; p.horns = 4; p.spines = 7; p.ribs = 4; p.torso = 1.25f; p.limb = 1.15f; p.cheek = true; p.tailEnd = 3; }
            case TRUE_KING -> { p.style = 3; p.horns = 8; p.domeL = 10; p.domeH = 6; p.domeW = 6.4f; p.carapace = true; p.plates = 2; p.spines = 10; p.ribs = 6; p.elbow = true; p.knee = true; p.torso = 1.5f; p.limb = 1.4f; p.tailEnd = 3; p.blade = true; p.clawL = 1.5f; p.cheek = true; p.brow = true; p.mand = true; p.tailT = 1.15f; }
            case CLONE_XENOMORPH -> { p.style = 1; p.domeL = 12; p.limb = 0.95f; p.spines = 4; p.ribs = 5; p.brow = true; p.domeW = 4.6f; p.cheek = true; }
            case WHITE_HYBRID -> { p.domeL = 11; p.domeW = 4f; p.limb = 0.9f; p.armK = 1.15f; p.upright = 0.15f; p.spines = 4; p.ribs = 5; p.cheek = true; p.mand = true; p.dreads = true; p.tailEnd = 1; p.tubeLen = 0; p.clawL = 1.25f; }
            case K_WARRIOR -> { p.style = 1; p.plates = 1; p.elbow = true; p.knee = true; p.torso = 1.2f; p.limb = 1.25f; p.ribs = 4; p.spines = 3; p.clawL = 1.2f; p.brow = true; }
            case K_FATALIS -> { p.style = 5; p.horns = 4; p.plates = 2; p.carapace = true; p.blade = true; p.elbow = true; p.knee = true; p.torso = 1.3f; p.limb = 1.3f; p.spines = 7; p.ribs = 5; p.clawL = 1.5f; p.tailEnd = 3; p.mand = true; p.cheek = true; }
            case WHITE_KING -> { p.style = 2; p.fan = 14; p.domeL = 10; p.dreads = true; p.mand = true; p.cheek = true; p.carapace = true; p.plates = 2; p.torso = 1.35f; p.limb = 1.3f; p.armK = 1.15f; p.spines = 7; p.ribs = 5; p.tailEnd = 3; p.tubeLen = 0; p.elbow = true; p.knee = true; }
            case WORKER -> { p.domeL = 10; p.limb = 0.8f; p.torso = 0.8f; p.tailEnd = 4; p.tubeLen = 0; p.ribs = 2; p.spines = 0; p.tailT = 0.8f; }
            case PALATINE -> { p.style = 5; p.mand = true; p.cheek = true; p.torso = 1.2f; p.limb = 1.2f; p.plates = 1; p.spines = 5; p.ribs = 4; p.elbow = true; p.tailEnd = 0; p.domeL = 14; p.horns = 4; }
            case SMASHER -> { p.style = 3; p.horns = 0; p.torso = 1.1f; p.limb = 1.1f; p.plates = 1; p.ribs = 4; p.spines = 3; p.domeW = 4.4f; p.brow = true; }
            case SPIKER -> { p.limb = 1.4f; p.neckK = 0.55f; p.domeL = 6; p.domeW = 5.4f; p.mand = true; p.spines = 9; p.torso = 1.5f; p.armK = 0.8f; p.legK = 0.85f; p.tubeLen = 0; p.ribs = 2; p.tailEnd = 2; p.tailSegs = 6; p.plates = 2; p.carapace = true; }
            case WARDEN -> { p.style = 2; p.fan = 12; p.carapace = true; p.plates = 2; p.torso = 1.3f; p.limb = 1.3f; p.horns = 3; p.tailEnd = 1; p.elbow = true; p.spines = 5; p.ribs = 4; }
            case HARBINGER -> { p.style = 4; p.sac = true; p.mand = true; p.domeW = 5.2f; p.spines = 6; p.ribs = 0; p.tubeLen = 0; p.clawL = 1.5f; p.limb = 1.1f; p.armK = 1.2f; }
            case PREDALIEN_QUEEN -> { p.style = 2; p.fan = 15; p.dreads = true; p.mand = true; p.cheek = true; p.carapace = true; p.plates = 1; p.torso = 1.2f; p.limb = 1.15f; p.spines = 6; p.tailEnd = 3; p.ribs = 4; }
            case SNATCHER -> { p.limb = 1.2f; p.armK = 1.35f; p.neckK = 0.8f; p.domeL = 9; p.spines = 3; p.ribs = 4; p.clawL = 1.2f; p.tubeLen = 0; }
            case PANTHER -> { p.limb = 0.85f; p.torso = 0.9f; p.domeL = 11; p.tailT = 0.85f; p.tubeLen = 0; p.ribs = 4; p.spines = 2; p.clawL = 1.3f; p.cheek = true; }
            case GOAT -> { p.style = 3; p.horns = 2; p.domeW = 3.6f; p.limb = 1.0f; p.torso = 1.0f; p.tubeLen = 0; p.ribs = 3; p.spines = 3; p.cheek = true; p.tailSegs = 6; p.tailEnd = 4; }
            case BAT -> { p.domeL = 9; p.limb = 0.7f; p.torso = 0.75f; p.armK = 1.35f; p.neckK = 1.1f; p.tubeLen = 0; p.ribs = 5; p.spines = 0; p.clawL = 1.5f; p.tailEnd = 4; p.tailT = 0.7f; }
            case WINTERBEAST -> { p.limb = 1.35f; p.torso = 1.35f; p.domeL = 10; p.domeW = 5.4f; p.plates = 2; p.spines = 6; p.carapace = true; p.tailEnd = 2; p.tailSegs = 6; p.tubeLen = 0; p.ribs = 3; p.mand = true; p.cheek = true; }
            case DOG_QUEEN -> { p.style = 2; p.fan = 11; p.domeL = 12; p.limb = 1.1f; p.torso = 1.05f; p.spines = 4; p.ribs = 4; p.carapace = true; p.tailEnd = 0; p.plates = 1; }
            case REAPER -> { p.domeL = 12; p.mand = true; p.cheek = true; p.spines = 0; p.tubeLen = 0; p.ribs = 4; p.clawL = 1.2f; p.brow = true; }
            case FLYING_QUEEN -> { p.style = 2; p.fan = 14; p.wings = true; p.carapace = false; p.limb = 0.95f; p.spines = 4; p.ribs = 5; p.tailEnd = 1; p.domeL = 14; }
            case VAMPIRIC_QUEEN -> { p.style = 2; p.fan = 12; p.wings = true; p.mand = true; p.cheek = true; p.limb = 0.9f; p.ribs = 6; p.spines = 3; p.domeL = 13; p.tailEnd = 3; }
            case IRRADIATED -> { p.style = 1; p.spines = 7; p.ribs = 5; p.cheek = true; p.limb = 1.05f; p.tailEnd = 3; }
            case SCORCHED -> { p.style = 5; p.spines = 5; p.ribs = 4; p.plates = 1; p.tailEnd = 0; p.clawL = 1.3f; }
            case TITAN -> { p.style = 3; p.horns = 6; p.domeW = 6.8f; p.carapace = true; p.plates = 2; p.spines = 11; p.ribs = 6; p.elbow = true; p.knee = true; p.torso = 1.5f; p.limb = 1.45f; p.tailEnd = 2; p.mand = true; p.cheek = true; p.clawL = 1.5f; }
            case WRAITH -> { p.domeL = 15; p.limb = 0.8f; p.armK = 1.2f; p.neckK = 1.2f; p.ribs = 7; p.spines = 2; p.clawL = 1.5f; p.tubeLen = 0; p.tailEnd = 1; }
            case ARMORED -> { p.style = 1; p.carapace = true; p.plates = 2; p.torso = 1.3f; p.limb = 1.3f; p.elbow = true; p.knee = true; p.spines = 4; p.ribs = 2; }
            case TUSKED -> { p.style = 3; p.horns = 0; p.mand = true; p.cheek = true; p.torso = 1.15f; p.limb = 1.15f; p.plates = 1; p.domeW = 4.6f; p.spines = 3; }
            case IMP -> { p.domeL = 8; p.limb = 0.8f; p.torso = 0.8f; p.tailSegs = 5; p.tailEnd = 4; p.tubeLen = 0; p.ribs = 2; p.spines = 0; }
            case ALPHA -> { p.style = 3; p.horns = 2; p.domeW = 4.8f; p.torso = 1.2f; p.limb = 1.2f; p.plates = 1; p.spines = 4; p.ribs = 4; p.cheek = true; p.elbow = true; }
            case CRAWLER -> { p.limb = 0.8f; p.torso = 0.85f; p.domeL = 11; p.spines = 5; p.ribs = 4; p.clawL = 1.4f; p.tailEnd = 4; p.armK = 1.1f; }
            case BIOMORPH -> { p.limb = 0.9f; p.torso = 0.95f; p.domeL = 16; p.domeH = 4.4f; p.ribs = 6; p.spines = 3; p.clawL = 1.3f; p.tubeLen = 0; p.tailEnd = 0; }
            case SIREN -> { p.domeL = 11; p.limb = 0.85f; p.torso = 0.85f; p.sac = true; p.spines = 2; p.ribs = 3; p.tailEnd = 4; }
            case SEVEN -> { p.spines = 1; p.domeL = 16; p.limb = 0.95f; p.ribs = 4; }
            case THRASHER -> { p.style = 3; p.horns = 0; p.domeW = 9f; p.domeH = 6f; p.limb = 1.5f; p.torso = 1.5f; p.mand = true; p.cheek = true; p.tubeLen = 0; p.ribs = 0; p.spines = 5; p.tailSegs = 3; p.tailEnd = 4; p.tailT = 1.3f; p.sac = true; p.legK = 0.8f; }
            case ULTRAMORPH -> { p.style = 2; p.fan = 10; p.domeL = 16; p.torso = 1.1f; p.limb = 1.1f; p.armK = 1.2f; p.legK = 1.15f; p.upright = 0.2f; p.ribs = 6; p.spines = 5; p.mand = true; p.plates = 1; p.tailEnd = 1; }
            case BIODRONE -> { p.spines = 0; p.ribs = 3; p.limb = 0.9f; p.tubeLen = 8; }
            case INFECTOID -> { p.style = 0; p.domeL = 8; p.domeW = 3.8f; p.limb = 0.7f; p.torso = 0.75f; p.armK = 1.2f; p.upright = 0.2f; p.bare = true; p.tailSegs = 0; p.ribs = 7; p.tubeLen = 0; p.spines = 0; p.neckK = 1.2f; }
            case SYMBIOTIC -> { p.style = 5; p.rider = true; p.domeL = 9; p.domeW = 5.6f; p.torso = 1.5f; p.limb = 1.5f; p.armK = 1.3f; p.plates = 2; p.spines = 5; p.ribs = 3; p.knee = true; p.elbow = true; p.tubeLen = 0; p.tailEnd = 2; p.tailSegs = 5; p.mand = true; }
            case BELLYBURSTER -> { p.domeL = 7; p.limb = 0.8f; p.torso = 0.8f; p.tubeLen = 0; p.ribs = 2; p.spines = 0; p.tailSegs = 5; p.tailEnd = 4; p.belly = true; }
            case BLOODBURSTER -> { p.style = 4; p.domeW = 4.2f; p.limb = 0.75f; p.torso = 0.8f; p.tubeLen = 0; p.ribs = 0; p.spines = 3; p.tailSegs = 4; p.tailEnd = 4; }
            case GOREBURSTER -> { p.style = 4; p.sac = true; p.domeW = 5f; p.limb = 0.9f; p.tubeLen = 0; p.ribs = 3; p.spines = 5; p.tailSegs = 3; p.tailEnd = 4; p.belly = true; }
            case NEOMORPH -> { p.style = 4; p.domeW = 4.6f; p.armK = 1.25f; p.legK = 1.1f; p.neckK = 1.25f; p.upright = 0.15f; p.limb = 0.7f; p.torso = 0.75f; p.tailSegs = 3; p.tailEnd = 4; p.spines = 6; p.ribs = 0; p.tubeLen = 0; p.clawL = 1.1f; }
            // gaunt, long-limbed, skull-faced humanoid: swept-back smooth cranium, hanging arms, bare ribcage, no tail
            case NEWBORN -> { p.style = 0; p.domeL = 10; p.domeH = 3.4f; p.domeW = 3.2f; p.headW = 0.85f; p.limb = 0.6f; p.torso = 0.62f; p.armK = 1.5f; p.legK = 1.25f; p.neckK = 1.6f; p.upright = 0.3f; p.bare = true; p.clawL = 1.6f; p.tailSegs = 0; p.ribs = 8; p.spines = 0; p.tubeLen = 0; }
            case DEACON -> { p.domeL = 17; p.domeH = 3.2f; p.domeW = 3.0f; p.limb = 0.8f; p.armK = 1.15f; p.upright = 0.25f; p.tailSegs = 5; p.tailEnd = 4; p.ribs = 3; p.tubeLen = 0; p.headW = 0.85f; p.neckK = 1.15f; }
            case BOILER -> { p.sac = true; p.domeL = 12; p.torso = 0.95f; p.spines = 9; p.limb = 0.8f; p.tailEnd = 4; p.ribs = 8; p.neckK = 1.2f; }
            case PROTOMORPH -> { p.style = 4; p.domeW = 4.4f; p.ribs = 1; p.spines = 5; p.limb = 0.95f; p.tailEnd = 1; p.cheek = true; p.tubeLen = 0; }
            case STALKER -> { p.limb = 1.2f; p.torso = 1.2f; p.tailT = 1.25f; p.spines = 3; p.ribs = 3; p.plates = 1; p.clawL = 1.2f; }
            case KING -> { p.style = 3; p.horns = 2; p.carapace = true; p.plates = 2; p.elbow = true; p.knee = true; p.torso = 1.2f; p.limb = 1.2f; p.domeL = 9; p.domeH = 5; p.ribs = 4; p.spines = 5; p.tubeLen = 0; }
            case CHARGER -> { p.style = 3; p.horns = 4; p.carapace = true; p.limb = 1.3f; p.tailSegs = 5; p.tailEnd = 2; p.domeL = 6; p.domeH = 5; p.domeW = 5; p.torso = 1.1f; p.tubeLen = 0; p.ribs = 0; }
            case BRUTE -> { p.style = 5; p.domeL = 8; p.domeW = 6.6f; p.domeH = 6; p.plates = 2; p.spines = 4; p.armK = 1.3f; p.torso = 1.4f; p.limb = 1.4f; p.knee = true; p.elbow = true; p.tailEnd = 0; p.tailT = 1.2f; p.ribs = 3; p.clawL = 1.3f; }
            case BULL_ALIEN -> { p.style = 3; p.horns = 2; p.torso = 1.25f; p.limb = 1.25f; p.cheek = true; p.mand = true; p.brow = true; p.domeL = 8; p.domeH = 5; p.domeW = 5; p.plates = 1; p.tailEnd = 1; p.ribs = 4; p.spines = 4; }
            case PATHOGEN_RUNNER -> { p.style = 4; p.limb = 0.8f; p.torso = 0.85f; p.tailSegs = 6; p.spines = 5; p.domeW = 4f; p.tailEnd = 4; p.ribs = 0; p.tubeLen = 0; p.clawL = 1.2f; p.mand = true; p.singleNail = true; }
            case PATHOGEN_BLIGHT -> { p.style = 4; p.sac = true; p.mand = true; p.domeW = 4.8f; p.spines = 4; p.tailEnd = 4; p.tubeLen = 0; p.ribs = 0; p.limb = 1.1f; }
            case PATHOGEN_BRUTE -> { p.style = 4; p.torso = 1.3f; p.limb = 1.2f; p.spines = 4; p.tailSegs = 0; p.domeW = 5f; p.ribs = 0; p.tubeLen = 0; }
            case PATHOGEN_QUEEN -> { p.style = 4; p.domeW = 6f; p.horns = 6; p.carapace = true; p.mand = true; p.torso = 1.2f; p.spines = 9; p.tubeLen = 0; p.ribs = 0; p.tailEnd = 1; }
            case BURSTER, EXPLODER -> { p.glowHead = true; p.style = 4; p.domeW = 6.4f; p.limb = 0.8f; p.torso = 0.85f; p.tubeLen = 0; p.cheek = true; p.spines = 6; p.clawL = 1.3f; p.ribs = 3; p.tailEnd = 0; p.tailT = 0.8f; }
            case PROWLER -> { p.style = 1; p.domeL = 9; p.brow = true; p.limb = 0.8f; p.tubeLen = 0; p.spines = 2; p.ribs = 6; p.tailT = 0.85f; p.tailEnd = 0; p.clawL = 1.4f; }
            case ARACHNID -> { p.style = 2; p.fan = 9; p.domeL = 9; p.domeH = 3.6f; p.mand = true; p.spines = 4; p.limb = 1.1f; p.tubeLen = 0; p.clawL = 1.3f; p.ribs = 2; p.tailEnd = 0; }
            default -> {}
        }
        // strain-wide family traits, applied to every rank so a faction reads as one family
        switch (c) {   // mutant silhouettes: neck, posture and trunk proportions pushed apart so the line-up reads at a glance
            case HULK_XENO -> { p.neckK = 0.62f; p.upright = 0f; p.torso *= 1.08f; }
            case HUSK_XENO -> { p.neckK = 0.7f; p.torso *= 1.05f; }
            case BONE_XENO -> { p.neckK = 1.55f; p.upright = 0.18f; p.torso *= 0.92f; }
            case WITHER_XENO -> { p.neckK = 1.3f; p.upright = 0.2f; p.torso *= 0.9f; }
            case VOID_XENO -> { p.neckK = 1.45f; p.upright = 0.28f; p.legK = Math.max(p.legK, 1.45f); }
            case BLAST_XENO -> { p.neckK = 0.85f; p.torso *= 1.1f; }
            case FROST_XENO, FROZEN_XENO -> { p.neckK = 0.8f; p.upright = 0.05f; }
            case HOG_XENO -> { p.neckK = 0.5f; p.torso *= 1.1f; }
            case JUNGLE_XENO -> { p.neckK = 1.2f; p.upright = 0.12f; p.armK = Math.max(p.armK, 1.5f); }
            case SHULKER_XENO -> { p.neckK = 0.7f; }
            case BLAZE_XENO -> { p.neckK = 0.9f; p.upright = 0.1f; }
            default -> {}
        }
        if (c.red()) { p.blade = true; p.cheek = true; p.clawL *= 1.25f; p.spines += 3; p.elbow = true; }                       // killers: blades, long claws, spiky
        if (c.kSeries()) { p.plates = Math.max(p.plates, 1); p.elbow = true; p.knee = true; p.limb *= 1.1f; p.torso *= 1.08f; p.ribs = Math.max(p.ribs, 3); }   // armoured
        if (c.cloneStrain() || c == Caste.CLONE_XENOMORPH) { p.ribs = Math.max(p.ribs, 5); p.spines = Math.max(p.spines, 4); p.brow = true; p.cheek = true; p.tailT *= 0.9f; }   // lab clones: sinewy, human-ish brow
        if (c.id.startsWith("pathogen") || c == Caste.HARBINGER || c == Caste.THRASHER) { p.mand = true; p.spines = Math.max(p.spines, 4); }                         // mutated: arthropod mandibles, quills
        return p;
    }

    public static LayerDefinition create(Caste c) {
        Sp sp = spec(c);
        cu = cv = rowH = 0;
        float T = sp.torso * 0.93f, L = sp.limb * 0.86f;       // slimmer frame, closer to the film suit / statue proportions
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", add(box(-3.5f * T, -3f, -4f, 7 * T, 6, 9), -2.5f, -4, -1, 5, 1.5f, 5), PartPose.offset(0, 11, 1));

        // torso
        CubeListBuilder bodyB = box(-2.55f * T, -9, -2.6f, 5.1f * T, 10, 5.2f);       // narrow waist (hourglass between ribcage and pelvis)
        add(bodyB, -3.1f * T, -2.6f, -3.1f, 6.2f * T, 2.8f, 6.2f);                       // flare into the pelvis
        for (int i = 0; i < 5; i++) add(bodyB, -0.9f - (i % 2) * 0.25f, -9f + i * 1.9f, 2.9f, 1.8f + (i % 2) * 0.5f, 1.3f, 1.5f + (i % 2) * 0.6f);   // vertebrae
        if (sp.belly) add(bodyB, -3.8f * T, -7, -5, 7.6f * T, 8, 2.4f);               // distended belly
        PartDefinition body = hips.addOrReplaceChild("body", bodyB, PartPose.offset(0, -1, -1));
        CubeListBuilder chestB = box(-3.8f * T, -8.5f, -3.8f, 7.6f * T, 9, 7.6f);
        add(chestB, -3.2f * T, -9.4f, -3.2f, 6.4f * T, 1.2f, 6.4f);                      // trapezius slope into the neck
        add(chestB, -3.0f * T, 0.2f, -3.0f, 6.0f * T, 1.0f, 6.0f);                       // ribcage tapers to the waist
        add(chestB, -3.4f * T, -7.6f, -4.4f, 6.8f * T, 2.6f, 0.7f); add(chestB, -3.0f * T, -5.0f, -4.4f, 6.0f * T, 2.4f, 0.7f);   // stepped, rounded breastplate
        for (int i = 0; i < sp.ribs; i++) { float y = -7.5f + i * (8f / Math.max(1, sp.ribs)); add(chestB, -4.3f * T, y, -3.4f, 0.8f, 0.8f, 6.8f); add(chestB, 3.5f * T, y, -3.4f, 0.8f, 0.8f, 6.8f);
            if (i % 2 == 0) add(chestB, -3.7f * T, y + 0.2f, -4.5f, 7.4f * T, 0.55f, 0.7f);         // rib arches wrapping the front
        }
        for (int i = 0; i < 6; i++) add(chestB, -0.9f - (i % 2) * 0.25f, -8.4f + i * 1.4f, 3.8f, 1.8f + (i % 2) * 0.5f, 1.1f, 1.5f + (i % 2) * 0.7f);   // vertebrae
        add(chestB, -3.3f * T, -8.9f, -4.0f, 6.6f * T, 0.8f, 0.8f); add(chestB, -0.6f, -7.2f, -4.3f, 1.2f, 6.2f, 0.7f);   // collarbone + sternum
        if (!c.mutant()) add(chestB, -3.4f * T, -10.8f, 0.2f, 6.8f * T, 3.4f, 5.8f);     // shoulder hump behind the neck, as on the reference renders
        for (int i = 0; i < sp.spines; i++) { float y = -8.2f + i * (8.4f / Math.max(1, sp.spines)); add(chestB, -0.5f, y, 5.1f, 1, 1.4f, 2.2f + (i % 2) * 1.2f); }
        CubeListBuilder quillB = CubeListBuilder.create();
        if (c == Caste.SPIKER) for (int i = 0; i < 16; i++) {   // quill field: long swept-back spikes across the back and shoulders
            float qx = -4.2f * T + (i % 4) * 2.8f * T, qy = -8.5f + (i / 4) * 2.4f, ql = 7 + (i * 5 % 4) * 2.2f;
            add(quillB, qx, qy, 4.6f, 0.9f, 0.9f, ql); add(quillB, qx + 0.15f, qy + 0.15f, 4.6f + ql, 0.6f, 0.6f, 2.2f);
        }
        for (int i = 0; i < sp.plates; i++) {
            add(chestB, -5.6f * T, -9f, -3.2f + i * 0.4f, 3.2f, 1.6f, 6.4f); add(chestB, 2.4f * T, -9f, -3.2f + i * 0.4f, 3.2f, 1.6f, 6.4f);   // pauldrons
            if (i == 1) { add(chestB, -6.2f * T, -9.8f, -2.2f, 1.2f, 3f, 1.2f); add(chestB, 5f * T, -9.8f, -2.2f, 1.2f, 3f, 1.2f); }          // shoulder spikes
        }
        if (sp.carapace) { add(chestB, -4.4f * T, -9.2f, 3.6f, 8.8f * T, 10.5f, 2.2f); add(chestB, -5f * T, -9.8f, 3.2f, 10f * T, 1.6f, 3f); }
        if (sp.belly) add(chestB, -4.4f * T, -2.5f, -4.8f, 8.8f * T, 3.5f, 2f);
        PartDefinition chest = body.addOrReplaceChild("chest", chestB, PartPose.offset(0, -8.5f, 0));
        if (c == Caste.SPIKER) chest.addOrReplaceChild("quills", quillB, PartPose.offset(0, 4f, 0));
        CubeListBuilder neckB = box(-2 * L, -5, -2 * L, 4 * L, 6, 4 * L);
        for (int i = 0; i < 3; i++) add(neckB, -2.35f * L, -4.4f + i * 2f, -2.35f * L, 4.7f * L, 0.7f, 4.7f * L);   // cartilage rings
        if (sp.rider) {   // a small rider xenomorph perched between the shoulders
            add(chestB, -1.6f, -12.5f, -1.2f, 3.2f, 3.6f, 3.6f); add(chestB, -1.2f, -15f, -2.4f, 2.4f, 2.6f, 5.5f); add(chestB, -0.5f, -15.8f, 0.5f, 1f, 1f, 3f);
            add(chestB, -2.8f, -11.5f, -0.5f, 0.9f, 3.4f, 0.9f); add(chestB, 1.9f, -11.5f, -0.5f, 0.9f, 3.4f, 0.9f);
        }
        if (sp.wings) for (int sg = -1; sg <= 1; sg += 2)   // folded membrane wings
            chest.addOrReplaceChild(sg > 0 ? "wingL" : "wingR", box(0, 0, 0, 0.8f, 16, 14), PartPose.offsetAndRotation(sg * 4.4f * T, -8.0f, 2.2f, 0.12f, sg * 0.3f, sg * -0.12f));   // folded: hangs down the flank
        PartDefinition neck = chest.addOrReplaceChild("neck", neckB, PartPose.offset(0, -7.5f, 0));

        // head
        float hw = sp.headW;
        CubeListBuilder headB = box(-2.2f * hw, -2.4f, -6, 4.4f * hw, 3.4f, 6);                 // low eyeless face; the dome rides over it from the very front
        if (!sp.bare) for (int i = 0; i < 4; i++) tooth(headB, (i - 1.5f) * 0.9f * hw - 0.2f, 0.8f, -6.25f, 0.45f, 1.0f + (i % 3 == 0 ? 0 : 0.4f), 0.4f);   // front fangs
        if (sp.brow) add(headB, -2.5f * hw, -3.2f, -6.3f, 5.0f * hw, 1.0f, 2.5f);
        PartDefinition head = neck.addOrReplaceChild("head", headB, PartPose.offset(0, -4.5f, 0));
        domeCu = 0;
        CubeListBuilder domeB = CubeListBuilder.create();
        if (sp.style != 4) {     // long "banana" cranium: four tapering sections whose top line slopes down toward the back
            float W = sp.domeW, H = sp.domeH, Ln = sp.domeL;
            dome(domeB, -W * 0.42f, -2.2f - H * 0.8f, -5.6f, W * 0.84f, H * 0.8f, 6.6f);        // tapered snout section, shorter overhang
            dome(domeB, -W * 0.3f, -1.9f - H * 0.55f, -6.3f, W * 0.6f, H * 0.55f, 1.0f);
            int seg = 8;   // smooth arc: eight slim sections whose crown tapers and drops toward the back, like the reference renders
            for (int i = 0; i < seg; i++) {
                float t0 = i / (float) seg, tm = (i + 0.5f) / seg;
                float bottom = -1.6f + 1.15f * tm * tm - 0.2f * tm;                 // underside rises toward the neck
                float hh = H * (0.85f - 0.42f * (float) Math.pow(tm, 1.25));       // crown height tapers
                float ww = W * (0.94f - 0.62f * (float) Math.pow(tm, 1.3));        // and narrows
                dome(domeB, -ww * 0.5f, bottom - hh, 1 + Ln * t0, ww, hh, Ln / seg + 0.12f);
            }
        }
        head.addOrReplaceChild("dome", domeB, PartPose.ZERO);
        CubeListBuilder rid = CubeListBuilder.create();
        if (sp.style == 1) for (int i = 0; i < 4; i++) box2(rid, -0.7f, -2.9f - sp.domeH * 0.8f + i * 0.25f, -5.5f + i * 3.4f, 1.4f, 1.2f, 2.8f);
        if (sp.style == 5) for (int i = 0; i < 6; i++) box2(rid, -0.5f, -4.6f - sp.domeH * 0.8f + (i % 2) * 0.8f + i * 0.2f, -5f + i * 2.4f, 1, 3.2f - (i % 2) * 0.8f, 1);
        if (sp.cheek) { box2(rid, -3.6f * hw, -2.4f, -4.5f, 1, 1, 3.5f); box2(rid, 2.6f * hw, -2.4f, -4.5f, 1, 1, 3.5f); box2(rid, -3.2f * hw, -1.2f, -2.5f, 0.8f, 0.8f, 2.5f); box2(rid, 2.4f * hw, -1.2f, -2.5f, 0.8f, 0.8f, 2.5f); }
        head.addOrReplaceChild("ridges", rid, PartPose.ZERO);
        CubeListBuilder fanB = CubeListBuilder.create();
        if (sp.style == 2) {   // royal crest: a swept-back armoured hood (centre keel, broad wings, flared rear edge with spines), not a flat board
            float fw = sp.fan;
            box2(fanB, -fw * 0.3f, -8.2f, -5, fw * 0.6f, 2.2f, 13);
            box2(fanB, -fw / 2, -7f, 1, fw, 1.6f, 12);
            box2(fanB, -fw * 0.42f, -9f, 11, fw * 0.84f, 2f, 7);
            box2(fanB, -fw * 0.2f, -10.2f, 3, fw * 0.4f, 1.4f, 11);
            if (sp.fan2) box2(fanB, -fw * 0.62f, -5.6f, 3, fw * 1.24f, 1.2f, 14);      // second, duller and wider under-layer (Empress)
            for (int i = 0; i < sp.horns; i++) { float x = -fw * 0.42f + 0.4f + i * ((fw * 0.84f - 0.8f) / Math.max(1, sp.horns - 1)); box2(fanB, x - 0.4f, -8.6f - (i % 2) * 0.6f, 17.5f, 0.8f, 0.8f, 4 + (i % 2) * 1.5f); }
        }
        head.addOrReplaceChild("fan", fanB, PartPose.rotation(-0.22f, 0, 0));
        CubeListBuilder hornB = CubeListBuilder.create();
        if (sp.style == 3) {   // armoured helmet crest (Crusher/Charger/Brute/King): thick sloped block with a brow overhang, side cheek plates
            float W = Math.max(5f, sp.domeW) + 2.2f;
            box2(hornB, -W / 2, -9.2f, -5.5f, W, 5.2f, 10.5f); box2(hornB, -W * 0.42f, -10.6f, -2f, W * 0.84f, 1.6f, 8f);
            box2(hornB, -W * 0.58f, -4.6f, -7.8f, W * 1.16f, 1.5f, 3.2f);
            box2(hornB, -W / 2 - 1.2f, -5.6f, -3.5f, 1.2f, 3.2f, 6f); box2(hornB, W / 2, -5.6f, -3.5f, 1.2f, 3.2f, 6f);
            if (sp.horns == 2 || sp.horns == 1) {   // great curved horns (Bull Alien): out, up, then tip
                for (int sg = -1; sg <= 1; sg += 2) {
                    float hx = sg > 0 ? W / 2 + 0.6f : -W / 2 - 2.6f;
                    box2(hornB, hx, -6.4f, -3.5f, 2.2f, 2.2f, 2.4f);
                    box2(hornB, hx + sg * 1.6f, -7.2f, -3.3f, 2.0f, 2.2f, 2.0f);
                    box2(hornB, hx + sg * 2.8f, -9.6f, -3.2f, 1.7f, 3.2f, 1.7f);
                    box2(hornB, hx + sg * 3.1f, -13.0f, -3.1f, 1.1f, 3.4f, 1.1f);
                }
            } else for (int i = 0; i < sp.horns; i++) {   // crown spikes along the crest
                float x = -W * 0.4f + i * (W * 0.8f / Math.max(1, sp.horns - 1));
                box2(hornB, x - 0.5f, -12.4f - (i % 2) * 1.2f, -0.5f + (i % 3) * 1.2f, 1.0f, 2.2f + (i % 2) * 1.2f, 1.0f);
            }
        }
        head.addOrReplaceChild("horns", hornB, PartPose.offsetAndRotation(0, -1, -1.5f, -0.1f, 0, 0));
        CubeListBuilder dr = CubeListBuilder.create();
        if (sp.dreads) for (int i = -3; i <= 3; i++) box2(dr, i * 1.1f, -1, 0, 0.9f, 8 + (i & 1) * 3 + 2, 0.9f);
        head.addOrReplaceChild("dreads", dr, PartPose.offsetAndRotation(0, -3.5f, 8, 0.5f, 0, 0));
        CubeListBuilder bulbB = CubeListBuilder.create();
        if (sp.glowHead) domeCu = 0;
        if (sp.style == 4) {
            float bw = sp.domeW + 1.6f;
            if (sp.glowHead) { dome(bulbB, -bw / 2, -7.5f, -4, bw, 8, 8); dome(bulbB, -bw * 0.35f, -9.2f, -1, bw * 0.7f, 2, 5.5f); }   // glowing cellular dome uses the reserved cranium strip
            else { box2(bulbB, -bw / 2, -7.5f, -4, bw, 8, 8); box2(bulbB, -bw * 0.35f, -9.2f, -1, bw * 0.7f, 2, 5.5f); }
            box2(bulbB, -2, -3.4f, -6.5f, 4, 3, 2.5f);
            for (int i = 0; i < sp.horns; i++) { float x = -4 + i * (8f / Math.max(1, sp.horns - 1)); box2(bulbB, x, -11f - (i % 2), 0.5f, 0.9f, 3f, 0.9f); }
        }
        head.addOrReplaceChild("bulb", bulbB, PartPose.ZERO);

        CubeListBuilder jawB = box(-2 * hw, 0, -6, 4 * hw, 1.5f, 8);
        for (int i = 0; i < (sp.bare ? 0 : 6); i++) { float th = 0.9f + (i % 2) * 0.6f; tooth(jawB, -1.8f * hw, -th, -5.6f + i * 1.1f, 0.45f, th, 0.45f); tooth(jawB, 1.35f * hw, -th, -5.6f + i * 1.1f, 0.45f, th, 0.45f); }
        if (!sp.bare) for (int i = 0; i < 4; i++) tooth(jawB, (i - 1.5f) * 0.85f * hw - 0.2f, -0.9f, -6.15f, 0.4f, 0.9f, 0.4f);
        if (sp.sac) box2(jawB, -2.6f, 1.4f, -4.5f, 5.2f, 3.2f, 6);
        head.addOrReplaceChild("jaw", jawB, PartPose.offset(0, 1, 1));
        // pharyngeal jaw: a telescoping, ribbed tube of four tapering segments ending in a ring of needle teeth around a centre fang
        CubeListBuilder inB = pipe(-1.0f, -0.9f, 0.5f, 2.0f, 1.8f, 9.5f);                    // hidden rear body
        add(inB, -1.5f, -1.2f, -1.2f, 3.0f, 2.4f, 1.3f);                                         // fleshy collar where it emerges
        inB.texOffs(0, 96).addBox(-0.95f, -0.85f, -2.2f, 1.9f, 1.7f, 3.0f).addBox(-0.8f, -0.7f, -5.0f, 1.6f, 1.4f, 2.9f).addBox(-0.65f, -0.55f, -7.2f, 1.3f, 1.1f, 2.3f).addBox(-0.5f, -0.45f, -8.8f, 1.0f, 0.9f, 1.7f);
        for (int k = 0; k < 4; k++) { float z = -2.0f - k * 2.2f, w = 1.1f - k * 0.17f; inB.texOffs(0, 96).addBox(-w - 0.15f, -w * 0.9f - 0.15f, z, 2 * w + 0.3f, 1.8f * w + 0.3f, 0.45f); }   // segment rings
        for (int i = 0; i < 3; i++) { tooth(inB, -1.0f, -0.2f, -2.4f - i * 2.2f, 0.35f, 0.7f, 0.4f); tooth(inB, 0.65f, -0.2f, -2.4f - i * 2.2f, 0.35f, 0.7f, 0.4f); }   // side teeth rows
        tooth(inB, -0.7f, -0.7f, -10.0f, 0.35f, 0.35f, 1.3f); tooth(inB, 0.35f, -0.7f, -10.0f, 0.35f, 0.35f, 1.3f); tooth(inB, -0.7f, 0.25f, -10.0f, 0.35f, 0.35f, 1.3f); tooth(inB, 0.35f, 0.25f, -10.0f, 0.35f, 0.35f, 1.3f);
        tooth(inB, -0.2f, -0.2f, -10.6f, 0.4f, 0.4f, 1.8f);                                      // centre fang
        head.addOrReplaceChild("inner", inB, PartPose.offset(0, 0.4f, -1.5f));
        CubeListBuilder upT = CubeListBuilder.create();
        for (int i = 0; i < (sp.bare ? 0 : 5); i++) { float th = 1.0f + ((i + 1) % 2) * 0.6f; tooth(upT, -2 * hw, 0.9f, -5.7f + i * 1.2f, 0.45f, th, 0.45f); tooth(upT, 1.55f * hw, 0.9f, -5.7f + i * 1.2f, 0.45f, th, 0.45f); }
        if (sp.mand) { box2(upT, -4f * hw, -0.5f, -8f, 1.3f, 1.3f, 6); box2(upT, 2.7f * hw, -0.5f, -8f, 1.3f, 1.3f, 6); box2(upT, -3.2f * hw, -0.4f, -9.5f, 0.8f, 0.8f, 2); box2(upT, 2.4f * hw, -0.4f, -9.5f, 0.8f, 0.8f, 2); }
        head.addOrReplaceChild("upperTeeth", upT, PartPose.ZERO);

        // back pipes
        float tl = Math.max(0.1f, sp.tubeLen);
        CubeListBuilder pl = pipe(-1.15f, -1.2f, 0, 2.3f, 2.3f, tl), pr = pipe(-1.15f, -1.2f, 0, 2.3f, 2.3f, tl);
        for (int i = 0; i * 2.6f < tl - 1; i++) { pl.addBox(-1.5f, -1.55f, 0.8f + i * 2.6f, 3.0f, 3.0f, 0.5f); pr.addBox(-1.5f, -1.55f, 0.8f + i * 2.6f, 3.0f, 3.0f, 0.5f); }   // ribbed rings
        PartDefinition tubL = chest.addOrReplaceChild("tubeL", pl, PartPose.offsetAndRotation(2 * T, -6.5f, 3.6f, 0.25f, 0.1f, 0));
        PartDefinition tubR = chest.addOrReplaceChild("tubeR", pr, PartPose.offsetAndRotation(-2 * T, -6.5f, 3.6f, 0.25f, -0.1f, 0));
        if (tl > 4) {   // tubes curve: a tapered tip segment bends up and out at the end
            tubL.addOrReplaceChild("tip", pipe(-0.8f, -0.8f, 0, 1.6f, 1.6f, tl * 0.45f), PartPose.offsetAndRotation(0, 0, tl - 0.4f, -0.55f, 0.18f, 0));
            tubR.addOrReplaceChild("tip", pipe(-0.8f, -0.8f, 0, 1.6f, 1.6f, tl * 0.45f), PartPose.offsetAndRotation(0, 0, tl - 0.4f, -0.55f, -0.18f, 0));
        }
        if (tl > 4) {   // second, lower pair of dorsal tubes like the film creature (four in all)
            tubL.addOrReplaceChild("low", pipe(-0.8f, -0.8f, 0, 1.6f, 1.6f, tl * 0.7f), PartPose.offsetAndRotation(0.7f, 4.4f, -0.6f, -0.22f, 0.14f, 0));
            tubR.addOrReplaceChild("low", pipe(-0.8f, -0.8f, 0, 1.6f, 1.6f, tl * 0.7f), PartPose.offsetAndRotation(-0.7f, 4.4f, -0.6f, -0.22f, -0.14f, 0));
        }

        // arms (index 0 = left / +x)
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            CubeListBuilder ab = box(-1.8f * L, -1, -1.8f * L, 3.6f * L, 4.8f, 3.6f * L);      // upper arm tapers toward the elbow
            add(ab, -1.4f * L, 3.6f, -1.4f * L, 2.8f * L, 5.9f, 2.8f * L);
            if (sp.elbow) add(ab, -0.5f, 6.5f, 1.4f * L, 1, 1, 3.4f);
            PartDefinition a = chest.addOrReplaceChild("arm" + n, ab, PartPose.offset(sx * 5 * T, -6.5f, 0));
            a.addOrReplaceChild("elbow", box(-1.6f * L, -1.6f * L, -1.6f * L, 3.2f * L, 3.2f * L, 3.2f * L), PartPose.offsetAndRotation(0, 8.5f, 0, 0.785f, 0, 0.785f));   // rounded joint caps
            a.addOrReplaceChild("shoulder", box(-1.9f * L, -1.9f * L, -1.9f * L, 3.8f * L, 3.8f * L, 3.8f * L), PartPose.offsetAndRotation(0, -0.4f, 0, 0.785f, 0, 0.785f));
            CubeListBuilder fb = box(-1.85f * L, 0, -1.85f * L, 3.7f * L, 5.2f, 3.7f * L);     // forearm: heavy muscle belly (as on the reference renders), then a slim wrist
            add(fb, -1.0f * L, 4.8f, -1.0f * L, 2.0f * L, 5.4f, 2.0f * L);
            add(fb, -0.3f, 1.2f, -1.3f * L - 0.45f, 0.6f, 6.5f, 0.6f);                 // bone ridge
            if (sp.blade) add(fb, -0.4f, 0.5f, 1.1f * L, 0.8f, 10.5f, 1.6f);
            PartDefinition f = a.addOrReplaceChild("fore", fb, PartPose.offset(0, 8.5f, 0));
            float fl = 3.8f * sp.clawL;
            PartDefinition hd = f.addOrReplaceChild("hand", box(-1.6f * L, 0, -2.5f, 3.2f * L, 1.6f, 4.5f), PartPose.offset(0, 10f, 0));
            for (int k = 0; k < 4; k++) {   // canon hand: three long two-jointed fingers + opposed thumb; each finger curls at two joints
                boolean th = k == 3;
                float fx = th ? (sx > 0 ? 1.45f * L : -2.05f * L) : (k - 1) * 1.05f * L - 0.3f;
                float f2 = th ? fl * 0.6f + 2f : fl * (k == 1 ? 1.45f : 1.25f), p1 = f2 * 0.55f, p2 = f2 - p1, w0 = th ? 0.65f : 0.6f;
                PartDefinition fg = hd.addOrReplaceChild("f" + k, add(box(0, 0, -p1, w0, 0.7f, p1), -0.1f, -0.45f, -0.3f, 0.8f, 0.7f, 1.0f),
                    PartPose.offsetAndRotation(fx, th ? 0.2f : 0.3f, th ? -1.6f : -2.4f, 0.1f, th ? (sx > 0 ? 0.5f : -0.5f) : (k - 1) * 0.12f, 0));
                CubeListBuilder dc = box(0.05f, 0.1f, -(p2 + 0.4f), 0.5f, 0.55f, p2 + 0.4f);
                if (!sp.singleNail || k == 1) add(dc, 0.1f, 0.3f, -(p2 + 0.4f) - 1.2f, 0.35f, 0.4f, 1.2f);
                fg.addOrReplaceChild("d", dc, PartPose.offset(0, 0, -p1));
            }
            PartDefinition a2 = chest.addOrReplaceChild("arm2" + n, box(-1, -1, -1, 2, 5, 2), PartPose.offset(sx * 3.6f * T, -3.5f, -2.5f));
            a2.addOrReplaceChild("fore", add(box(-0.8f, 0, -0.8f, 1.6f, 5, 1.6f), -1.2f, 5, -2.2f, 2.4f, 1, 3), PartPose.offset(0, 4.5f, 0));
        }
        // legs
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 1 : -1;
            String n = s == 0 ? "L" : "R";
            CubeListBuilder tb = box(-2.4f * L, -1, -2.5f * L, 4.8f * L, 5.5f, 5.0f * L);      // thigh: heavy top, slim knee
            add(tb, -1.8f * L, 4.0f, -1.9f * L, 3.6f * L, 4.6f, 3.8f * L);
            if (sp.knee) add(tb, -0.5f, 7f, -3.2f * L - 2, 1, 1.4f, 2.4f);
            PartDefinition t = hips.addOrReplaceChild("thigh" + n, tb, PartPose.offset(sx * 4.2f * T, 0, 1));
            t.addOrReplaceChild("knee", box(-1.7f * L, -1.7f * L, -1.7f * L, 3.4f * L, 3.4f * L, 3.4f * L), PartPose.offsetAndRotation(0, 8f, 0, 0.785f, 0, 0.785f));
            CubeListBuilder shB = box(-1.7f * L, -0.5f, -1.7f * L, 3.4f * L, 4.2f, 3.4f * L);   // shin: calf bulge, slim ankle
            add(shB, -1.15f * L, 3.5f, -1.15f * L, 2.3f * L, 5.0f, 2.3f * L);
            add(shB, -0.4f, 0.8f, 1.5f * L, 0.8f, 5.5f, 0.8f);                           // calf ridge
            PartDefinition sh = t.addOrReplaceChild("shin", shB, PartPose.offset(0, 8, 0));
            CubeListBuilder ft = box(-1.8f * L, 0, -5, 3.6f * L, 1.6f, 6.5f);
            for (int k = -1; k <= 1; k++) { add(ft, k * 1.1f * L - 0.4f, 0.4f, -7.5f, 0.8f, 1, 3); add(ft, k * 1.1f * L - 0.25f, 0.55f, -8.6f, 0.5f, 0.5f, 1.2f); }   // toes with claw tips
            add(ft, -0.4f, 0.2f, 1.4f, 0.8f, 1.2f, 1.8f);                                // heel spur
            sh.addOrReplaceChild("foot", ft, PartPose.offset(0, 8, 0));
        }
        if (c.attacks.contains(Attack.LAY)) {   // ovipositor: a ribbed tube under the tail root that unfolds during egg-laying, with an egg bulge sliding down it
            CubeListBuilder ob = box(-1.3f, -1.3f, 0, 2.6f, 2.6f, 11);
            for (int i = 0; i < 4; i++) add(ob, -1.7f, -1.7f, 1.2f + i * 2.6f, 3.4f, 3.4f, 0.6f);
            add(ob, -2.1f, -2.1f, 11, 4.2f, 4.2f, 3.2f);
            hips.addOrReplaceChild("ovi", ob, PartPose.offsetAndRotation(0, 2.5f, 3.5f, -0.75f, 0, 0));
        }
        // tail
        int N = Math.max(1, sp.tailSegs);
        float taper = 0.42f * 8f / N;
        PartDefinition tp = hips.addOrReplaceChild("tail0", box(-2.4f * sp.tailT, -2.4f * sp.tailT, 0, 4.8f * sp.tailT, 4.8f * sp.tailT, 7), PartPose.offset(0, -0.5f, 4.5f));
        for (int i = 1; i < TAIL; i++) {
            float w = Math.max(1.2f, (4.4f - i * taper) * sp.tailT);
            CubeListBuilder cb = box(-w / 2, -w / 2, 0, w, w, 7);
            add(cb, -w * 0.18f, -w / 2 - 0.5f, 2.2f, w * 0.36f, 0.6f, 1.6f); add(cb, -w * 0.12f, w / 2 - 0.1f, 2.6f, w * 0.24f, 0.5f, 1.2f);   // serrated ridges
            if (i == N - 1) tailEnd(cb, sp.tailEnd, w);
            tp = tp.addOrReplaceChild("tail" + i, cb, PartPose.offset(0, 0, 7));
        }
        mutantParts(c, chest, head, T, L);
        return LayerDefinition.create(mesh, 256, 128);
    }

    private static CubeListBuilder box2(CubeListBuilder b, float x, float y, float z, float w, float h, float d) { return add(b, x, y, z, w, h, d); }

    private static void tailEnd(CubeListBuilder cb, int kind, float w) {
        switch (kind) {
            case 0 -> {                                                                                                   // long swept leaf blade with a keel
                add(cb, -0.4f, -3.8f, 2, 0.8f, 7.6f, 3.2f); add(cb, -0.35f, -3.2f, 5, 0.7f, 6.4f, 3.2f); add(cb, -0.3f, -2.5f, 8, 0.6f, 4.9f, 3.2f);
                add(cb, -0.25f, -1.7f, 11, 0.5f, 3.3f, 3.2f); add(cb, -0.2f, -0.9f, 14, 0.4f, 1.8f, 2.6f); add(cb, -0.6f, -0.6f, 2, 1.2f, 1.2f, 15);
            }
            case 1 -> { add(cb, -0.5f, -0.5f, 5, 1, 1, 10); add(cb, -0.4f, -1.6f, 8, 0.8f, 3.2f, 1.2f); add(cb, -0.4f, -1.2f, 11.5f, 0.8f, 2.4f, 1.2f); } // spear + barbs
            case 2 -> { add(cb, -3f, -3f, 1, 6, 6, 6); add(cb, -0.6f, -4.6f, 2, 1.2f, 1.6f, 1.2f); add(cb, -0.6f, 3f, 2, 1.2f, 1.6f, 1.2f); add(cb, -4.6f, -0.6f, 2, 1.6f, 1.2f, 1.2f); add(cb, 3f, -0.6f, 2, 1.6f, 1.2f, 1.2f); } // spiked club
            case 3 -> { add(cb, -2.2f, -2.8f, 3, 0.8f, 5.6f, 6); add(cb, 1.4f, -2.8f, 3, 0.8f, 5.6f, 6); add(cb, -0.4f, -1.2f, 6, 0.8f, 2.4f, 5); }  // twin blade
            default -> { }
        }
    }

    // ------------------------------------------------------------------ animation
    private float P;  // current chest pitch (world, rad)


    /** Silhouette extras for the mutant lineage: hulk shoulders, blaze orbiting shields, shulker lids, hog tusks, jungle vines, frost crystals, void spare arms, blast sacs, wither cape. */
    private static void mutantParts(Caste c, PartDefinition chest, PartDefinition head, float T, float L) {
        switch (c) {
            case HULK_XENO, HUSK_XENO -> {
                chest.addOrReplaceChild("trapL", box(2f * T, -11, -3.5f, 6 * T, 6, 8), PartPose.rotation(0, 0, 0.25f));
                chest.addOrReplaceChild("trapR", box(-8f * T, -11, -3.5f, 6 * T, 6, 8), PartPose.rotation(0, 0, -0.25f));
                chest.addOrReplaceChild("hump", add(box(-3.5f, -13.5f, 1, 7, 6, 7), -2f, -15.5f, 2.5f, 4, 3, 4), PartPose.ZERO);
            }
            case BLAZE_XENO -> { for (int i = 0; i < 3; i++) chest.addOrReplaceChild("shield" + i, add(box(-3.5f, -6, -13, 7, 12, 1.4f), -2.2f, -4.5f, -14.2f, 4.4f, 9, 1f), PartPose.offset(0, -1, 0)); }
            case SHULKER_XENO -> {
                chest.addOrReplaceChild("lidL", add(box(-1, -2, -1, 11, 3, 14), 1, -3.2f, 0, 8, 1.2f, 11), PartPose.offset(0.5f, -10.5f, 2));
                chest.addOrReplaceChild("lidR", add(box(-10, -2, -1, 11, 3, 14), -9, -3.2f, 0, 8, 1.2f, 11), PartPose.offset(-0.5f, -10.5f, 2));
            }
            case HOG_XENO -> {
                for (int s = -1; s <= 1; s += 2) {
                    PartDefinition t = head.addOrReplaceChild(s < 0 ? "tuskR" : "tuskL", box(-1f, -1f, -8, 2, 2, 8), PartPose.offsetAndRotation(s * 3.2f, 2.5f, -7f, 0.55f, 0, 0));
                    t.addOrReplaceChild("tip", box(-0.8f, -0.8f, -6, 1.6f, 1.6f, 6), PartPose.offsetAndRotation(0, 0, -7.5f, 0.8f, 0, 0));
                }
                CubeListBuilder mane = CubeListBuilder.create();
                for (int i = 0; i < 6; i++) add(mane, -0.8f, -13.5f - (i % 2) * 2, -3 + i * 1.8f, 1.6f, 7 + (i % 2) * 2, 1.6f);
                chest.addOrReplaceChild("maneHog", mane, PartPose.ZERO);
            }
            case JUNGLE_XENO -> {
                for (int s = -1; s <= 1; s += 2) {
                    PartDefinition v = chest.addOrReplaceChild(s < 0 ? "vineR" : "vineL", box(-0.6f, 0, -0.6f, 1.2f, 7, 1.2f), PartPose.offset(s * (5.2f * T + 1.5f), -8, 0.5f));
                    PartDefinition v2 = v.addOrReplaceChild("v2", box(-0.5f, 0, -0.5f, 1f, 7, 1f), PartPose.offset(0, 7, 0));
                    v2.addOrReplaceChild("v3", add(box(-0.4f, 0, -0.4f, 0.8f, 6, 0.8f), -1.6f, 5.5f, -0.1f, 3.2f, 0.3f, 2.4f), PartPose.offset(0, 7, 0));   // leaf at the tip
                }
            }
            case FROST_XENO -> {
                for (int i = 0; i < 7; i++) {
                    float len = 8 + (i * 5 % 4) * 2.5f;
                    chest.addOrReplaceChild("crystal" + i, box(-1.1f, -len, -1.1f, 2.2f, len, 2.2f), PartPose.offsetAndRotation(-4.2f * T + i * 1.4f * T, -6 - (i % 3) * 1.2f, 3.2f + (i % 2) * 0.8f, 0.5f + (i % 3) * 0.12f, 0, -0.3f + i * 0.1f));
                }
            }
            case VOID_XENO -> {
                for (int s = -1; s <= 1; s += 2) {
                    PartDefinition a = chest.addOrReplaceChild(s < 0 ? "xarmR" : "xarmL", add(box(-0.6f, 0, -0.6f, 1.2f, 10, 1.2f), -0.9f, 9.2f, -0.9f, 1.8f, 1.6f, 1.8f), PartPose.offsetAndRotation(s * 2.8f, -6.5f, 4.5f, 0.6f, 0, s * 0.7f));
                    a.addOrReplaceChild("f", add(box(-0.5f, 0, -0.5f, 1f, 10, 1f), -0.8f, 9, -2.2f, 1.6f, 3.5f, 0.8f), PartPose.offset(0, 10, 0));
                }
            }
            case BLAST_XENO -> {
                chest.addOrReplaceChild("sacL", box(-2.7f, -2.7f, -2.7f, 5.4f, 5.4f, 5.4f), PartPose.offset(5.4f * T, -7.5f, 5.5f));
                chest.addOrReplaceChild("sacR", box(-2.7f, -2.7f, -2.7f, 5.4f, 5.4f, 5.4f), PartPose.offset(-5.4f * T, -7.5f, 5.5f));
            }
            case WITHER_XENO -> chest.addOrReplaceChild("cape", add(box(-5 * T, 0, 0, 10 * T, 16, 0.8f), -3 * T, 14, 0.2f, 6 * T, 3, 0.6f), PartPose.offsetAndRotation(0, -9, 5.6f, 0.15f, 0, 0));
            default -> {}
        }
    }

    /** Idle personality and attack-specific moves of the mutant lineage, layered on top of the shared pose. */
    private void mutantMotion(Caste c, Attack at, float t, float age, float aw) {
        float idle = 1 - aw;
        float pulse = (float) Math.pow(Math.max(0, Mth.sin(age * 0.063f)), 8);        // a short beat every ~100 ticks
        switch (c) {
            case HULK_XENO, HUSK_XENO -> { for (int s = 0; s < 2; s++) { arm[s].xRot -= 0.7f * pulse * idle; fore[s].xRot -= 0.5f * pulse * idle; } chest.xRot += 0.12f * pulse * idle; hips.zRot += 0.03f * Mth.sin(age * 0.04f) * idle; }   // fist pounding, heavy sway
            case BONE_XENO -> { head.zRot += 0.28f * pulse * idle; jaw.xRot += Math.abs(Mth.sin(age * 1.4f)) * 0.22f * pulse * idle; chest.yRot += 0.05f * Mth.sin(age * 0.05f) * idle; }      // neck crack, jaw clatter
            case BLAST_XENO -> { float th = 1 + 0.05f * Mth.sin(age * 0.3f) * idle; chest.xScale *= th; chest.zScale *= th; hips.yScale *= 1 + 0.03f * Mth.sin(age * 0.3f + 1) * idle; }              // throbbing
            case VOID_XENO -> { head.yRot += 0.07f * Mth.sin(age * 1.7f) * Mth.sin(age * 0.23f) * idle; chest.zRot += 0.05f * Mth.sin(age * 0.9f) * Math.max(0, Mth.sin(age * 0.11f)) * idle; }    // flicker
            case FROST_XENO, FROZEN_XENO -> { for (int s = 0; s < 2; s++) arm[s].zRot += 0.025f * Mth.sin(age * 2.2f + s) * idle; }                                                              // shiver
            case BLAZE_XENO -> { chest.xRot += 0.035f * Mth.sin(age * 0.2f) * idle; for (int s = 0; s < 2; s++) arm[s].zRot += (s == 0 ? 0.12f : -0.12f) * idle; }                              // heat haze sway, arms flared
            case WITHER_XENO -> { for (int s = 0; s < 2; s++) { arm[s].zRot += (s == 0 ? 0.18f : -0.18f) * idle; fore[s].xRot -= 0.25f * idle; } hips.zRot += 0.03f * Mth.sin(age * 0.07f) * idle; }  // blades held ready
            case HOG_XENO -> { head.xRot += 0.22f * pulse * idle; neck.xRot += 0.1f * pulse * idle; }                                                                                              // pawing
            case JUNGLE_XENO -> { chest.zRot += 0.05f * Mth.sin(age * 0.06f) * idle; head.zRot += 0.04f * Mth.sin(age * 0.06f + 1) * idle; }
            default -> {}
        }
        if (at == Attack.NONE) return;
        float hit = at.hit;
        switch (at) {
            case BONE_BARRAGE -> {                                                       // draw the bow: lead arm out, other hand pulled to the cheek, recoil on release
                float draw = kf(t, 10, 0, 22, 1, hit + 1, 1, hit + 8, 0.2f, at.duration, 0);
                arm[0].xRot = Mth.lerp(draw * aw, arm[0].xRot, -1.45f); arm[0].zRot = Mth.lerp(draw * aw, arm[0].zRot, 0.1f); fore[0].xRot = Mth.lerp(draw * aw, fore[0].xRot, 0f);
                arm[1].xRot = Mth.lerp(draw * aw, arm[1].xRot, -1.0f); fore[1].xRot = Mth.lerp(draw * aw, fore[1].xRot, -2.2f); head.xRot -= 0.15f * draw * aw;
            }
            case FIRE_VOLLEY -> { float k = Mth.sin(t * 0.8f) * kf(t, hit - 4, 0, hit, 1, hit + 16, 1, hit + 22, 0); arm[0].xRot -= (0.9f + 0.5f * k) * aw; arm[1].xRot -= (0.9f - 0.5f * k) * aw; chest.xRot -= 0.12f * aw; }   // alternate thrusts
            case THORN_PATCH -> { float k = kf(t, 0, 0, hit - 3, 1, hit + 6, 0.8f, at.duration, 0); for (int s = 0; s < 2; s++) { arm[s].xRot += 1.5f * k * aw; fore[s].xRot += 0.3f * k * aw; } chest.xRot += 0.35f * k * aw; }       // hands plunge into the ground
            case GUST -> { float k = kf(t, 0, 0, hit - 2, 1, hit + 14, 1, at.duration, 0); for (int s = 0; s < 2; s++) { arm[s].zRot += (s == 0 ? -1 : 1) * 0.9f * k * aw * Mth.sin(t * 0.7f); arm[s].xRot -= 0.9f * k * aw; } }   // sweeping blower arms
            case SHELL_GUARD -> { float k = kf(t, 0, 0, 8, 1, hit + 34, 1, hit + 44, 0); chest.xRot += 0.5f * k * aw; head.xRot += 0.5f * k * aw; for (int s = 0; s < 2; s++) { arm[s].zRot += (s == 0 ? -0.6f : 0.6f) * k * aw; arm[s].xRot -= 0.7f * k * aw; } }   // hunker down
            case CLONE_SPLIT -> { float k = kf(t, 0, 0, hit, 1, hit + 30, 1, at.duration, 0); for (int s = 0; s < 2; s++) arm[s].zRot += (s == 0 ? 1 : -1) * 1.1f * k * aw; head.xRot -= 0.3f * k * aw; }
            case MINION_BURST -> { float k = kf(t, 0, 0, hit - 2, 1, hit + 4, 0); chest.xScale *= 1 + 0.25f * k * aw; chest.zScale *= 1 + 0.2f * k * aw; head.xRot -= 0.4f * k * aw; jaw.xRot += 0.6f * k * aw; }       // heave and spit
            case QUICKSAND -> { float k = kf(t, 0, 0, hit - 4, 1, hit + 4, 0.4f, at.duration, 0); for (int s = 0; s < 2; s++) arm[s].xRot += 1.1f * k * aw; chest.xRot += 0.3f * k * aw; }
            case ICE_SMASH -> { float up = kf(t, 0, 0, hit - 8, 1, hit, 0.1f, at.duration, 0); for (int s = 0; s < 2; s++) { arm[s].xRot -= 1.4f * up * aw; arm[s].zRot += (s == 0 ? 0.2f : -0.2f) * up * aw; } }
            default -> {}
        }
    }


    /** Individual and status looks: colour drift per entity, veteran claws, freezing / poison / wither tints, and the face's mood. */
    private void individual(XenoEntity e, float age) {
        float sd = e.seed(), vet = e.veteran();
        float drift = (sd - 0.5f) * 0.14f;                                           // +-7% brightness, a little warm or cool
        tintR = 1 + drift * 1.2f - 0.035f * vet; tintG = 1 + drift - 0.035f * vet; tintB = 1 + drift * 0.8f - 0.035f * vet;
        if (e.getTicksFrozen() > 40) { float f = Mth.clamp(e.getTicksFrozen() / (float) e.getTicksRequiredToFreeze(), 0, 1); tintR *= 1 - 0.35f * f; tintG *= 1 - 0.1f * f; tintB *= 1 + 0.25f * f; }
        if (e.hasEffect(net.minecraft.world.effect.MobEffects.POISON)) { tintR *= 0.85f; tintB *= 0.8f; tintG *= 1.12f; }
        if (e.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) { tintR *= 0.6f; tintG *= 0.6f; tintB *= 0.62f; }
        if (e.getHealth() < e.getMaxHealth() * 0.25f) { tintG *= 0.9f; tintB *= 0.9f; }       // flagging
        for (int s = 0; s < 2; s++) hand[s].zScale *= 1 + 0.09f * vet;                    // veterans grow longer claws
        if (e.isAggressive() && e.attack() == com.xenomorph.entity.Attack.NONE) {         // hostile face: jaw agape, inner jaw flicking
            float fl = Mth.sin(age * 0.35f + sd * 6) * 0.5f + 0.5f;
            jaw.xRot += 0.14f + 0.1f * fl; inner.z -= 1.4f * fl; head.xRot -= 0.05f;
        }
    }

    /** Per-frame motion of the mutant extras (needs entity state, so it lives outside pose()). */
    private void mutantAnim(XenoEntity e, float age, float pt) {
        float t = age;
        if (shields != null) {
            int layers = e.shieldLayers();
            for (int i = 0; i < 3; i++) { shields[i].visible = i < layers; shields[i].yRot = t * 0.07f + i * Mth.TWO_PI / 3f; shields[i].y = -1 + Mth.sin(t * 0.1f + i * 2) * 1.2f; }
        }
        if (lids != null) {
            float open = e.shellTicks > 0 ? 0.02f : 0.5f + 0.12f * Mth.sin(t * 0.05f);
            if (e.shellTicks > 0 && e.shellTicks < 6) open = 0.9f;           // pops open at the end of the guard
            lids[0].zRot = -open; lids[1].zRot = open;
        }
        if (vines != null) for (int s = 0; s < 2; s++) {
            float sw = Mth.sin(t * 0.09f + s * 1.7f);
            vines[s].xRot = arm[s].xRot * 0.8f + 0.1f + sw * 0.12f; vines[s].zRot = (s == 0 ? 0.25f : -0.25f) + sw * 0.1f;
            vines[s].getChild("v2").xRot = 0.25f + Mth.sin(t * 0.11f + s) * 0.18f; vines[s].getChild("v2").getChild("v3").xRot = 0.3f + Mth.sin(t * 0.13f + s * 2) * 0.22f;
        }
        if (xarms != null) {
            boolean atk = e.attack() != com.xenomorph.entity.Attack.NONE;
            for (int s = 0; s < 2; s++) {
                float sg = s == 0 ? 1 : -1, w = Mth.sin(t * 0.08f + s * 2.2f);
                xarms[s].xRot = (atk ? 1.2f : 0.6f) + w * 0.15f; xarms[s].zRot = sg * ((atk ? 1.0f : 0.7f) + w * 0.12f);
                xarms[s].getChild("f").xRot = 0.5f + Mth.sin(t * 0.1f + s) * 0.3f;
            }
        }
        if (sacs != null) {
            boolean charging = e.attack() == com.xenomorph.entity.Attack.BLAST_CHARGE;
            float sw = 1 + 0.12f * Mth.sin(t * 0.2f) + (charging ? 0.6f * Mth.clamp(e.attackTick / 56f, 0, 1.2f) : 0);
            for (ModelPart sac : sacs) sac.xScale = sac.yScale = sac.zScale = sw;
        }
        if (crystals != null) for (int i = 0; i < 7; i++) crystals[i].zRot = (-0.3f + i * 0.1f) + Mth.sin(t * 0.05f + i) * 0.03f;
        if (cape != null) { float walk = Mth.clamp(e.walkAnimation.speed() * 2f, 0, 1); cape.xRot = 0.15f + walk * 0.35f + Mth.sin(t * 0.1f) * 0.05f; cape.zRot = Mth.sin(t * 0.07f) * 0.05f; }
        if (tusks != null) for (ModelPart tk : tusks) tk.xRot = 0.55f + (e.attack() == com.xenomorph.entity.Attack.HORN_TOSS || e.attack() == com.xenomorph.entity.Attack.CHARGE ? 0.3f * Mth.sin(e.attackTick * 0.4f) : 0);
    }

    @Override
    public void setupAnim(XenoEntity e, float limbSwing, float amt, float age, float headYaw, float headPitch) {
        float pt = Minecraft.getInstance().getFrameTime();
        pose(e.caste, Mth.lerp(pt, e.bipedAmtO, e.bipedAmt), e.attack(), e.attackTick + pt, limbSwing, amt, age, headYaw, headPitch, Mth.lerp(pt, e.climbAmtO, e.climbAmt), Mth.lerp(pt, e.hangAmtO, e.hangAmt));
        if (e.deathTime > 0) {                      // death throes: small castes keel over onto their side, giants sag at the knees and slump forward
            float d = Mth.clamp((e.deathTime + pt) / 20f, 0, 1), eased = d * d * (3 - 2 * d);
            if (e.caste.weight() < 1.2f) { hips.zRot += 1.4f * eased; hips.y += 5 * eased; head.zRot += 0.4f * eased; }
            else { hips.y += 7 * eased; chest.xRot += 0.55f * eased; neck.xRot += 0.45f * eased; head.xRot += 0.35f * eased; for (int s = 0; s < 2; s++) arm[s].xRot += 0.4f * eased; }
            for (int i = 0; i < TAIL; i++) tail[i].xRot += 0.08f * eased;
        }
        individual(e, age);
        mutantAnim(e, age, pt);
    }

    /** Pure pose function (also used by the offline preview tool). b = biped blend, t = ticks into attack. */
    public void pose(Caste c, float bipedBlend, Attack at, float t, float limbSwing, float amt, float age, float headYaw, float headPitch) { pose(c, bipedBlend, at, t, limbSwing, amt, age, headYaw, headPitch, false, false); }

    public void pose(Caste c, float bipedBlend, Attack at, float t, float limbSwing, float amt, float age, float headYaw, float headPitch, boolean climb, boolean hang) { pose(c, bipedBlend, at, t, limbSwing, amt, age, headYaw, headPitch, climb ? 1f : 0f, hang ? 1f : 0f); }

    /** climbAmt / hangAmt are 0..1 blend weights so wall and ceiling poses ease in and out. */
    public void pose(Caste c, float bipedBlend, Attack at, float t, float limbSwing, float amt, float age, float headYaw, float headPitch, float climbAmt, float hangAmt) {
        if (at != Attack.NONE && at.hit > 0 && at.duration > at.hit + 8) t = t < at.hit ? t : t < at.hit + 2.5f ? at.hit : t - 2.5f;   // hit-stop: pose freezes 2.5 ticks on impact
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
        P = Mth.lerp(b, 1.38f, 0.52f - sp.upright * 0.6f * b);          // reference renders: strongly hunched, head slung forward          // film stance: torso close to upright, not a dinosaur lean
        float H = Mth.lerp(b, 10.5f, 14.7f) * Math.min(1.4f, 1 + (spec(c).legK - 1) * 1.45f);
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
        float[] legOv = new float[]{0, 0, 0, 0};
        float[] stepTz = new float[2], stepLift = new float[2];       // footwork offsets during melee attacks  // blend, tz, ty
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
        float calm = (1 - aw) * (1 - Mth.clamp(amt * 3f, 0, 1));
        if (fer >= 0.6f) {                                                                 // fierce: periodic threat display
            float th = (float) Math.pow(Math.max(0, Mth.sin(age * 0.045f + c.ordinal())), 12);
            jawOpen += 0.5f * th * calm; headX -= 0.22f * th * calm; chestX -= 0.1f * th * calm; neckY += 0.12f * Mth.sin(age * 0.9f) * th * calm; tailYaw[0] += 0.3f * Mth.sin(age * 0.5f) * th * calm;
        } else if (fer < 0.35f) {                                                          // wary: slow head sweeps, shy crouch
            headY += 0.4f * Mth.sin(age * 0.028f + c.ordinal()) * calm; neckY += 0.12f * Mth.sin(age * 0.028f + c.ordinal()) * calm; crouch += 0.8f * calm;
        }
        float bob = -Mth.abs(Mth.sin(ph)) * 0.9f * spd * (0.4f + b * 0.6f) * wt;
        // spine undulation
        float und = Mth.sin(ph) * 0.22f * spd * (1.2f - 0.6f * b);
        bodyY += und; chestY -= und * 1.3f; neckY += und * 0.6f; headY -= und * 0.5f;
        float walkRoll = Mth.sin(ph) * 0.05f * spd;
        float lightK = Mth.clamp((1 - wt) / 0.4f, 0, 1), heavyK = Mth.clamp((wt - 1.2f) / 1.0f, 0, 1);   // gait character: light = springy bounding, heavy = rolling lumber
        bob *= 1 + 1.2f * lightK + 0.8f * heavyK; walkRoll *= 1 + 2.2f * heavyK;
        neckX += Mth.sin(ph * 2) * 0.05f * spd * (1 + lightK * 1.5f - heavyK * 0.4f); headX += Mth.sin(ph * 2 + 0.6f) * 0.04f * spd * (1 + lightK);   // head bobs with the stride: quick on light castes, stolid on heavy ones
        bodyX += lightK * Mth.sin(ph * 2) * 0.06f * spd; chestY += heavyK * Mth.sin(ph) * 0.1f * spd;
        // tail
        for (int i = 0; i < TAIL; i++) {
            float w = (0.14f + 0.12f * spd) * (0.6f + i * 0.14f);
            tailYaw[i] = Mth.sin(age * 0.05f - i * 0.55f + ph * 0.0f) * 0.08f * (0.6f + i * 0.1f) + Mth.sin(ph * 1.0f - i * 0.7f) * w * spd;
            tailPitch[i] = Mth.lerp(b, 0.012f * i, i == 0 ? 0 : -0.1f) + Mth.sin(ph * 2 - i * 0.6f) * 0.05f * spd;   // biped: tail hangs, then sweeps back up
        }
        tailBaseX = Mth.lerp(b, -0.08f, 0.5f);   // biped: tail arches higher for balance, quad: trails

        // arms loco
        for (int s = 0; s < 2; s++) {
            float sign = s == 0 ? 1 : -1;
            float swing = Mth.sin(ph + (s == 0 ? Mth.PI : 0));
            // biped: relaxed hang with ready claws, counter swing
            A[s][0] = -0.4f + swing * 0.55f * spd * b - breathe * 0.04f;
            A[s][1] = sign * (-0.25f - 0.1f * b);                 // splay outward (yaw)
            A[s][2] = 0;
            A[s][3] = 0.62f + 0.1f * (1 - spd) - swing * 0.2f * spd;   // arms hang nearly straight, no folded elbows
            A[s][4] = 0.75f;                       // long fingers hang, claws down
            A[s][5] = 0;
        }

        // ---------- wall climbing: upright against the wall, hands and feet alternately reaching
        float cph = age * 0.55f;
        float cl = at == Attack.NONE ? climbAmt * (1 - hangAmt) : 0, hg = hangAmt;
        boolean climbing = cl > 0.01f, hang = hg > 0.01f;
        if (climbing) {
            P = Mth.lerp(cl, P, 0.1f); crouch -= 1.5f * cl;
            for (int s = 0; s < 2; s++) {
                float sg = s == 0 ? 1 : -1, sw = Mth.sin(cph + s * Mth.PI);
                A[s][0] = Mth.lerp(cl, A[s][0], -2.55f + 0.5f * sw); A[s][1] = Mth.lerp(cl, A[s][1], sg * -0.3f); A[s][2] = Mth.lerp(cl, A[s][2], 0); A[s][3] = Mth.lerp(cl, A[s][3], 0.45f + 0.45f * Math.max(0, Mth.sin(cph + s * Mth.PI + 1.57f))); A[s][4] = Mth.lerp(cl, A[s][4], 0.5f); A[s][5] = cl;
            }
            legOv[0] = cl; legOv[1] = -3.2f; legOv[2] = 7.5f; legOv[3] = 2.2f;
            headX += -0.7f * cl; chestX += -0.12f * cl; chestY += Mth.sin(cph) * 0.12f * cl;
            for (int i = 0; i < TAIL; i++) tailPitch[i] += 0.1f * cl;
        }
        // ---------- ceiling hang: belly to the ceiling, claws gripping, tail dangling
        if (hang) {
            hRoll += Mth.PI * hg;
            for (int s = 0; s < 2; s++) { A[s][0] = Mth.lerp(hg, A[s][0], -2.9f + 0.05f * Mth.sin(age * 0.07f + s)); A[s][1] = Mth.lerp(hg, A[s][1], (s == 0 ? 1 : -1) * -0.15f); A[s][3] = Mth.lerp(hg, A[s][3], 0.15f); A[s][4] = Mth.lerp(hg, A[s][4], -0.3f); A[s][5] = Math.max(A[s][5], hg); }
            headX += 0.9f * hg; jawOpen += (0.15f + 0.2f * Math.max(0, Mth.sin(age * 0.05f))) * hg;
            for (int i = 0; i < TAIL; i++) { tailPitch[i] += 0.12f * hg; tailYaw[i] += Mth.sin(age * 0.06f - i * 0.5f) * 0.08f * hg; }
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
                case DOUBLE_CLAW, RIB_CRUSH, WITHER_SLASH -> {
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
                case SPIT, BONE_SHOT -> {
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
                case SCREECH, ROAR_BLAST -> {
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
                case SLAM, SEISMIC, TELE_SMASH, QUICKSAND, ICE_SMASH -> {
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
                case DROP_STRIKE -> {
                    hRoll = kf(t, 0, Mth.PI, 9, 0);
                    crouch += kf(t, 0, 0, 9, -1f, 12, 4f, 20, 1.5f, 30, 0); chestX += kf(t, 0, 0, 9, -0.2f, 12, 0.6f, 30, 0); headX += kf(t, 0, 0, 10, -0.3f, 13, 0.5f, 30, 0);
                    jawOpen += kf(t, 0, 0.3f, 8, 1.4f, 14, 0.4f, 30, 0.1f); innerExt += kf(t, 6, 0, 10, 6, 16, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -2.9f, 8, -2.2f, 12, -1.0f, 20, -0.9f, 30, -0.6f); A[s][1] = sg * kf(t, 0, -0.15f, 8, -1.0f, 12, 0.2f, 30, -0.2f); A[s][3] = kf(t, 0, 0.2f, 8, 0.3f, 12, 0.9f, 30, 0.9f); A[s][5] = aw;
                    }
                    legOv[0] = kf(t, 0, 0, 6, 1, 10, 1, 12, 0); legOv[1] = 3f; legOv[2] = 6f;
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
                case GRAB_SLAM, HURL -> {
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
                case SPORE, SHELL_GUARD -> {
                    float sw = kf(t, 0, 0, 18, 1, 20, 1.15f, 26, 0.1f, 44, 0);
                    chestScale = 1 + 0.4f * sw; neckScale = 1 + 0.2f * sw;
                    headX += kf(t, 0, 0, 16, -0.8f, 20, -0.9f, 26, 0.2f, 44, 0); chestX += kf(t, 0, 0, 16, -0.4f, 20, -0.45f, 26, 0.1f, 44, 0);
                    jawOpen += kf(t, 0, 0.1f, 16, 1.2f, 26, 1.3f, 34, 0.2f, 44, 0.1f); crouch += kf(t, 0, 0, 18, 1.2f, 20, -0.5f, 30, 0.5f, 44, 0);
                    chestZ += Mth.sin(t * 3) * 0.04f * kf(t, 8, 0, 18, 1, 20, 0);
                    for (int s = 0; s < 2; s++) { float sg = s == 0 ? 1 : -1; A[s][0] = -0.9f; A[s][1] = sg * (-0.2f - 0.9f * sw); A[s][2] = sg * -0.5f * sw; A[s][3] = 0.5f; A[s][5] = aw * Math.min(1, sw * 2); }
                }
                case LOB, THROW_BLOCK, WITHER_BOMB -> {
                    headX += kf(t, 0, 0, 9, -1.0f, 12, -0.9f, 14, 0.7f, 22, 0.2f, 30, 0); neckX += kf(t, 0, 0, 9, -0.6f, 13, 0.5f, 30, 0);
                    neckScale = 1 + 0.5f * kf(t, 0, 0, 9, 1, 13, 0, 22, 0); chestX += kf(t, 0, 0, 9, -0.6f, 14, 0.4f, 30, 0);
                    jawOpen += kf(t, 0, 0.1f, 8, 0.7f, 13, 1.4f, 18, 0.3f, 30, 0.1f); crouch += kf(t, 0, 0, 9, -0.5f, 14, 2f, 30, 0);
                    A[0][0] = kf(t, 0, -0.6f, 9, -2.8f, 14, -0.4f, 30, -0.6f); A[0][1] = 0.4f; A[0][3] = 0.5f; A[0][5] = aw;
                    A[1][0] = -1.0f; A[1][1] = -0.4f; A[1][3] = 1.2f; A[1][5] = aw * 0.8f;
                }
                case VOLLEY, FIREARM, BONE_BARRAGE, FIRE_VOLLEY, SHULKER_STORM, ICICLE_VOLLEY -> {
                    float v = Math.max(0, Mth.sin((t - 8f) * 1.25f)) * kf(t, 8, 0, 9, 1, 25, 1, 27, 0);
                    headX += -0.5f * v - 0.2f * aw; jawOpen += 0.1f + 1.3f * v; neckScale = 1 + 0.35f * v; chestScale = 1 + 0.07f * v; chestX += -0.2f * v; crouch += 1.2f * aw;
                    for (int s = 0; s < 2; s++) { A[s][0] = -1.0f; A[s][1] = (s == 0 ? 1 : -1) * 0.4f; A[s][3] = 1.2f; A[s][5] = aw * 0.8f; }
                }
                case VOMIT, GUST -> {
                    float up = kf(t, 0, 0, 8, 1, 36, 1, 42, 0), pulse = Mth.sin(t * 0.9f) * kf(t, 10, 0, 14, 1, 34, 1, 38, 0);
                    headX += -1.1f * up; neckX += -0.5f * up; chestX += -0.5f * up + pulse * 0.05f; jawOpen += 0.1f + 1.4f * up; innerExt += 4 * up;
                    chestScale = 1 + 0.12f * pulse; neckScale = 1 + 0.4f * up + 0.2f * pulse; crouch += 1.2f * up;
                    for (int s = 0; s < 2; s++) { A[s][0] = -0.9f; A[s][1] = (s == 0 ? 1 : -1) * 0.6f; A[s][3] = 0.8f; A[s][5] = aw * 0.8f; }
                }
                case DETONATE, BLAST_CHARGE -> {
                    float sw = kf(t, 0, 0, 34, 1, 36, 1), shake = Mth.sin(t * 2.9f) * sw * sw;
                    chestScale = 1 + 0.9f * sw; neckScale = 1 + 0.4f * sw; bodyY += shake * 0.12f; chestZ += shake * 0.08f; headZ += shake * 0.2f; jawOpen += 0.1f + 1.4f * sw;
                    crouch += 1.5f * sw + shake * 0.6f; innerExt += 3 * sw;
                    for (int s = 0; s < 2; s++) { A[s][0] = -1.0f + shake * 0.5f; A[s][1] = (s == 0 ? 1 : -1) * 0.7f; A[s][3] = 0.5f; A[s][5] = aw; }
                }
                case SUMMON, CLONE_SPLIT -> {
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
                case FACE_TOSS, MINION_BURST -> {
                    chestScale = 1 + 0.15f * kf(t, 0, 0, 8, 1, 24, 1, 30, 0);
                    chestX += kf(t, 0, 0, 11, -0.55f, 14, 0.5f, 19, -0.55f, 22, 0.5f, 32, 0); headX += kf(t, 0, 0, 11, -0.3f, 14, 0.4f, 19, -0.3f, 22, 0.4f, 32, 0);
                    jawOpen += kf(t, 0, 0.1f, 11, 1f, 14, 0.4f, 19, 1f, 22, 0.4f, 32, 0.1f); crouch += kf(t, 0, 0, 8, 1.5f, 24, 1.5f, 32, 0);
                    for (int s = 0; s < 2; s++) {
                        float sg = s == 0 ? 1 : -1;
                        A[s][0] = kf(t, 0, -0.6f, 5, -1.9f, 11, -3.0f, 14, -0.5f, 17, -1.7f, 19, -3.0f, 22, -0.5f, 32, -0.6f); A[s][1] = sg * 0.3f; A[s][3] = 0.4f; A[s][5] = aw;
                    }
                }
                case WEB, THORN_PATCH -> {
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
            // footwork: the lead foot lifts and steps into the strike, the rear foot pushes off (skipped for leaps / rolls / flips that pose the legs themselves)
            if (legOv[0] < 0.01f && at != Attack.POUNCE && at != Attack.LEAP_SLASH && at != Attack.DEATH_ROLL && at != Attack.DROP_STRIKE && at != Attack.SHADOW_STRIKE) {
                int lead = at == Attack.CLAW_R || ((at == Attack.DOUBLE_CLAW || at == Attack.FRENZY || at == Attack.BLADE_FLURRY) && Mth.sin(t * 0.4f) > 0) ? 1 : 0;
                float lift = kf(t, Math.max(0, hit - 7), 0, hit - 3, 1, hit, 0), plant = kf(t, Math.max(0, hit - 3), 0, hit, 1, hit + 10, 0.7f, dur, 0);
                stepTz[lead] = -3.2f * plant; stepLift[lead] = 2.4f * lift * aw;
                stepTz[1 - lead] = 1.6f * plant;
            }
            // secondary motion for every attack: tail counter-swings against the torso twist, head lags behind, body lunges into the impact
            {
                float imp2 = kf(t, Math.max(0, hit - 2), 0, hit, 1, hit + 5, 0);
                for (int i = 0; i < TAIL; i++) tailYaw[i] -= chestY * 0.5f * (1 + i * 0.15f) * aw;
                headY -= chestY * 0.35f * aw; neckY -= chestY * 0.2f * aw;
                hipZ -= imp2 * 0.8f; chestX += imp2 * 0.08f; headX += imp2 * 0.05f;
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
        hAct = Mth.lerp(hg, hAct, 4.5f);
        hAct = Mth.lerp(cl, hAct, 12f);
        hips.y = 24 - hAct + bob;
        hips.z = 1 + hipZ + (1 - b) * -1.5f;
        hips.zRot = walkRoll + hRoll; hips.xRot = flip;
        // quad body is long: shorten "bipedal" crouch effect on hips height etc handled by IK
        body.xRot = P + bodyX; body.yRot = bodyY; body.zRot = walkRoll * -1.5f;
        float aim = P - Mth.lerp(b, 0.15f, 0.42f);                    // how much the head must tilt back up
        chest.xRot = chestX - aim * 0.22f; chest.yRot = chestY; chest.zRot = chestZ;
        neck.xRot = neckX - aim * 0.42f; neck.yRot = neckY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.35f;
        head.xRot = headX - aim * 0.34f - 0.22f * b + Mth.clamp(headPitch, -45, 45) * Mth.DEG_TO_RAD * 0.7f;
        head.yRot = headY + Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.3f; head.zRot = headZ;
        jaw.xRot = Mth.clamp(jawOpen, 0, 1.5f);
        inner.z = 5.0f - innerExt * 1.6f;   // retracted inside the mouth at rest, telescopes about 12 units out on a bite inner.xRot = jaw.xRot * 0.15f;
        float br = Mth.sin(age * 0.11f), iw = (1 - aw) * (1 - Mth.clamp(amt * 3f, 0, 1));   // idle breathing: chest swell, neck and head drift
        chest.xScale = chestScale * (1 + 0.03f * br * iw); chest.yScale = chestScale * (1 + 0.02f * br * iw); chest.zScale = chestScale * (1 + 0.04f * br * iw);
        neck.xRot += 0.035f * br * iw; head.xRot -= 0.025f * br * iw; head.yRot += 0.05f * Mth.sin(age * 0.047f) * iw;
        neck.xScale = neck.zScale = neckScale;
        root.yRot = rootSpin;
        tubeL.xRot = Mth.lerp(b, -0.45f, 0.35f) + breathe * 0.03f - und * 0.2f; tubeR.xRot = tubeL.xRot;     // canon: the dorsal pipes rise up and away from the back
        tubeL.yRot = Mth.lerp(b, 0.1f, 0.34f) + und * 0.3f; tubeR.yRot = -tubeL.yRot;

        // extra Queen arms flutter
        for (int s = 0; s < 2; s++) {
            float sg = s == 0 ? 1 : -1;
            arm2[s].xRot = Mth.lerp(a2w, -0.9f + Mth.sin(age * 0.07f + s * 2) * 0.12f + (at == Attack.NONE ? 0 : -0.6f * aw), a2p[s]);
            arm2[s].zRot = sg * -0.25f; fore2[s].xRot = -1.0f + Mth.sin(age * 0.09f + s) * 0.15f;
        }

        if (c == Caste.SPIKER) {                       // quills flare on the volley wind-up, then hang spent and short until they regrow
            float fl = 0;
            if (at == Attack.VOLLEY) fl = kf(t, 0, 0, Math.max(1, at.hit - 4), 1, at.hit, 1, at.hit + 3, -0.5f, at.duration, -0.2f);
            ModelPart q = chest.getChild("quills"); q.xScale = 1 + 0.3f * fl; q.yScale = 1 + 0.35f * fl; q.zScale = 1 + 0.4f * fl;
        }
        // tail counter-motion on every melee strike: tip cocks up on wind-up, then a whip wave runs down the tail after impact
        if (at != Attack.NONE && at.hit > 0 && at != Attack.TAIL_STAB && at != Attack.TAIL_SWEEP && !climbing && !hang) {
            for (int i = 1; i < TAIL; i++) {
                float tt = t - i * 0.7f, ht = at.hit;
                tailPitch[i] -= 0.03f * i * aw * kf(t, 0, 0, Math.max(1, ht - 6), 1, ht, 0.4f, ht + 3, 0);
                tailPitch[i] += 0.035f * i * aw * kf(tt, ht - 1, 0, ht + 1, 1, ht + 6, -0.4f, ht + 12, 0);
                tailYaw[i] += 0.07f * aw * Mth.sin((t - ht) * 0.5f - i * 0.6f) * kf(t, ht, 0, ht + 2, 1, ht + 14, 0);
            }
        }

        // tail
        // authored with negative = raise; model +xRot lifts the tip, so flip here
        tail0.xRot = -(tailBaseX + tailPitch[0]); tail0.yRot = tailYaw[0];
        for (int i = 1; i < TAIL; i++) { tail[i].xRot = -tailPitch[i]; tail[i].yRot = tailYaw[i]; }

        if (ovi != null) {
            float ov = at == Attack.LAY ? kf(t, 0, 0, 12, 1, 56, 1, 68, 0) : 0;
            ovi.visible = ov > 0.02f;
            ovi.zScale = ov; ovi.xScale = ovi.yScale = 0.6f + 0.4f * ov;
            if (at == Attack.LAY) { float ps = Mth.sin(t * 0.55f) * kf(t, 12, 0, 20, 1, 50, 1, 60, 0); ovi.xScale *= 1 + 0.18f * ps; ovi.yScale *= 1 + 0.18f * ps; ovi.xRot = -0.75f + 0.12f * ps; }
        }
        // ---------- legs via IK
        float footH = Mth.lerp(hg, hAct - 1.6f, 14.5f);
        for (int s = 0; s < 2; s++) {
            float u = ((ph * legSpeed / Mth.TWO_PI) + (s == 0 ? 0 : 0.5f)) % 1f; if (u < 0) u += 1;
            float stride = Mth.lerp(b, 4.8f, 5.6f) * spd;
            float tz, lift;
            if (u < 0.58f) { tz = Mth.lerp(u / 0.58f, -stride, stride); lift = 0; }
            else { float k = (u - 0.58f) / 0.42f; tz = Mth.lerp(smooth(k), stride, -stride); lift = Mth.sin(k * Mth.PI) * (1.6f + 1.8f * spd) * (1 - 0.3f * heavyK + 0.35f * lightK); }
            tz += Mth.lerp(b, 0.5f, 1.0f) + stepTz[s];                // keep feet slightly forward of hip, plus attack footwork
            float ty = footH - lift - stepLift[s] - 2.2f * b * (1 - legOv[0]);   // ankle held high: digitigrade stance
            tz = Mth.lerp(legOv[0], tz, legOv[1]); ty = Mth.lerp(legOv[0], ty, legOv[2] + legOv[3] * Mth.sin(cph + s * Mth.PI));
            float lk = sp.legK;
            float[] a = ik(tz, ty, 8f * lk, 8f * lk, true);
            thigh[s].yScale = lk;
            thigh[s].xRot = Mth.clamp(a[0], -2.2f, 2.2f); shin[s].xRot = Mth.clamp(a[1] - a[0], -2.7f, 2.7f);   // knee never folds the shin flat against the thigh
            foot[s].xRot = -a[1] + Mth.lerp(legOv[0], 0.0f, 0.9f) + lift * 0.06f + 0.62f * b * (1 - legOv[0]);   // heel lifted: toe-walking stance like the film suit
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
            float el = Mth.lerp(wq, A[s][3], Math.max(qEl, -2.55f));   // never fold the forearm past ~146 degrees (low crouches used to double it back on itself)
            float hnd = Mth.lerp(wq, A[s][4], qHand);
            float yaw = Mth.lerp(wq, A[s][1], (s == 0 ? 1 : -1) * -0.05f);
            float roll = Mth.lerp(wq, A[s][2], 0);
            // attack override (world values)
            float w = A[s][5];
            if (at != Attack.NONE || climbing || hang) {
                pitch = Mth.lerp(w, pitch, A[s][0]); el = Mth.lerp(w, el, A[s][3]); hnd = Mth.lerp(w, hnd, A[s][4]);
                yaw = Mth.lerp(w, yaw, A[s][1]); roll = Mth.lerp(w, roll, A[s][2]);
            }
            if (c == Caste.HULK_XENO || c == Caste.HUSK_XENO || c == Caste.HOG_XENO || c == Caste.FROZEN_XENO) { hand[s].xScale = hand[s].yScale = hand[s].zScale = 1.55f; fore[s].xScale = fore[s].zScale = 1.18f; }   // hammer fists
            if (c == Caste.VOID_XENO || c == Caste.WITHER_XENO) { fore[s].yScale = 1.25f; }                                                                       // longer forearms
            if (c == Caste.PATHOGEN_BRUTE || (c == Caste.PATHOGEN_QUEEN && s == 1)) { hand[s].xScale = hand[s].yScale = hand[s].zScale = 1.9f; fore[s].xScale = fore[s].zScale = 1.25f; }
            arm[s].yScale = sp.armK;
            arm[s].xRot = pitch - body.xRot - chest.xRot; arm[s].yRot = yaw; arm[s].zRot = roll;
            fore[s].xRot = el; hand[s].xRot = hnd;
            // finger curl: relaxed hook at rest, spread open on wind-up, clench on impact
            float curl = 0.35f + 0.15f * Mth.sin(age * 0.08f + s * 2f);
            if (at != Attack.NONE && !climbing && !hang) {
                float ht = at.hit, tt = t;
                float cc = kf(tt, 0, 0.35f, Math.max(1, ht - 6), -0.25f, ht, 1.0f, ht + 8, 0.7f, Math.max(ht + 9, at.duration), 0.35f);
                curl = Mth.lerp(w, curl, cc);
            } else if (climbing || hang) curl = 0.9f;
            for (int k = 0; k < 4; k++) { ModelPart fp = hand[s].getChild("f" + k); fp.xRot = 0.1f + curl * 0.5f; fp.getChild("d").xRot = curl * 0.9f + 0.15f; }
        }
        if (c.mutant() && climbAmt < 0.3f && hangAmt < 0.3f) mutantMotion(c, at, t, age, aw);
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float bl, float a) {
        root.render(ps, vc, light, overlay, r * tintR, g * tintG, bl * tintB, a);
    }
}
