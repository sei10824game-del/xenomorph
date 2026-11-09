package com.xenomorph.client;

import com.xenomorph.entity.HunterEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Vanilla humanoid rig plus per-kind extras (UV region (0,32) is a plain metal/skin patch in the textures). */
final class HunterModel extends HumanoidModel<HunterEntity> {
    HunterModel(ModelPart root) { super(root); }

    static LayerDefinition create(HunterEntity.Kind k) {
        MeshDefinition m = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition head = m.getRoot().getChild("head"), body = m.getRoot().getChild("body");
        if (k == HunterEntity.Kind.PREDATOR) {
            for (int i = -3; i <= 3; i++)
                head.addOrReplaceChild("dread" + i, CubeListBuilder.create().texOffs(0, 40).addBox(i * 1.15f - 0.4f, -7, 3.6f, 0.8f, 11 + ((i & 1) == 0 ? 3 : 0), 0.8f), PartPose.rotation(0.22f, 0, 0));
            head.addOrReplaceChild("mand", CubeListBuilder.create().texOffs(0, 32).addBox(-3.9f, 0.5f, -5.4f, 1.1f, 3.4f, 1.1f).addBox(2.8f, 0.5f, -5.4f, 1.1f, 3.4f, 1.1f).addBox(-1.5f, 2.5f, -5.4f, 3f, 1.6f, 1f), PartPose.ZERO);
            head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 32).addBox(-4.4f, -8.8f, -4.6f, 8.8f, 1.6f, 1.2f), PartPose.ZERO);
            body.addOrReplaceChild("cannon", CubeListBuilder.create().texOffs(0, 32).addBox(-7.2f, -4.2f, -1.2f, 3f, 2.4f, 6f).addBox(-6.6f, -4.8f, 3.6f, 1.8f, 1.8f, 2.4f), PartPose.ZERO);
            body.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 32).addBox(-4.6f, 0.4f, -2.6f, 9.2f, 3.6f, 5.2f), PartPose.ZERO);
        } else {
            head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 16).addBox(-3.4f, -9.6f, 0.2f, 6.8f, 5.6f, 7f), PartPose.rotation(0.12f, 0, 0));
            body.addOrReplaceChild("wrap", CubeListBuilder.create().texOffs(0, 32).addBox(-4.7f, 10.2f, -2.7f, 9.4f, 5f, 5.4f), PartPose.ZERO);
        }
        return LayerDefinition.create(m, 64, 64);
    }
}
