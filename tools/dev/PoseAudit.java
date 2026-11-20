import com.xenomorph.client.*;
import com.xenomorph.entity.*;
import net.minecraft.client.model.geom.ModelPart;
import java.lang.reflect.*;
import java.util.*;

public class PoseAudit {
    static Field fChildren;
    static void collect(String path, ModelPart p, Map<String, ModelPart> out) throws Exception {
        out.put(path, p);
        Map<String, ModelPart> ch = (Map<String, ModelPart>) fChildren.get(p);
        for (var e : ch.entrySet()) collect(path.isEmpty() ? e.getKey() : path + "/" + e.getKey(), e.getValue(), out);
    }
    public static void main(String[] a) throws Exception {
        fChildren = ModelPart.class.getDeclaredField("children"); fChildren.setAccessible(true);
        Map<String, Integer> issues = new TreeMap<>(); List<String> samples = new ArrayList<>();
        for (Caste c : Caste.values()) for (float bip : new float[]{0f, 1f}) {
            if ((bip == 1 && !c.canBiped()) || (bip == 0 && !c.canQuad())) continue;
            ModelPart root = XenoModel.create(c).bakeRoot();
            XenoModel m = new XenoModel(root);
            Map<String, ModelPart> parts = new LinkedHashMap<>(); collect("", root, parts);
            List<Attack> ats = new ArrayList<>(); ats.add(Attack.NONE); ats.addAll(c.attacks);
            for (Attack at : ats) {
                int dur = Math.max(1, at.duration);
                for (int t = 0; t <= dur; t += 2) for (float limb : new float[]{0f, 3f}) {
                    m.pose(c, bip, at, t, limb, limb > 0 ? 1f : 0f, 50f + t, 0, 0, 0f, 0f);
                    for (var e : parts.entrySet()) {
                        ModelPart p = e.getValue();
                        float[] v = {p.xRot, p.yRot, p.zRot, p.x, p.y, p.z, p.xScale, p.yScale, p.zScale};
                        for (int i = 0; i < v.length; i++) if (Float.isNaN(v[i]) || Float.isInfinite(v[i])) { issues.merge("NaN", 1, Integer::sum); if (samples.size() < 40) samples.add("NaN " + c + " " + at + " t=" + t + " " + e.getKey() + " idx" + i); }
                        String key = e.getKey();
                        // extreme rotations on limb joints (|x|>3.3 rad ~ 190deg) indicate a flipped joint
                        if ((key.endsWith("fore") || key.endsWith("shin") || key.endsWith("thighL") || key.endsWith("thighR") || key.contains("arm") && !key.contains("/")) ) {
                            if (Math.abs(p.xRot) > 2.75f) { issues.merge("rot>195deg " + (key.contains("fore") ? "fore" : key.contains("shin") ? "shin" : key.contains("thigh") ? "thigh" : "arm"), 1, Integer::sum); if (samples.size() < 40) samples.add(String.format("rot %s %s bip=%.0f t=%d %s x=%.2f", c, at, bip, t, key, p.xRot)); }
                        }
                        // knee must bend backwards (shin.xRot >= -0.1 in our convention?) report sign stats separately
                        if (Math.abs(p.x) > 60 || Math.abs(p.y) > 80 || Math.abs(p.z) > 80) { issues.merge("offset>60 " + key.substring(key.lastIndexOf('/') + 1), 1, Integer::sum); if (samples.size() < 40) samples.add(String.format("far %s %s t=%d %s (%.1f,%.1f,%.1f)", c, at, t, key, p.x, p.y, p.z)); }
                    }
                }
            }
        }
        System.out.println("issues: " + issues);
        samples.forEach(System.out::println);
    }
}
