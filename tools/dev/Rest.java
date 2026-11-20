import com.xenomorph.client.*;
import com.xenomorph.entity.*;
import net.minecraft.client.model.geom.ModelPart;
public class Rest {
    static ModelPart[] arr(XenoModel m, String n) throws Exception { var f = XenoModel.class.getDeclaredField(n); f.setAccessible(true); return (ModelPart[]) f.get(m); }
    public static void main(String[] a) throws Exception {
        for (String id : a) {
            Caste c = Caste.valueOf(id.toUpperCase());
            for (float bip : new float[]{1f, 0f}) {
                if ((bip == 1 && !c.canBiped()) || (bip == 0 && !c.canQuad())) continue;
                XenoModel m = new XenoModel(XenoModel.create(c).bakeRoot());
                m.pose(c, bip, Attack.NONE, 0, 0, 0, 100f, 0, 0, 0f, 0f);
                java.lang.reflect.Field[] fs = XenoModel.class.getDeclaredFields();
                java.util.function.Function<String, ModelPart> g = n -> { try { var f = XenoModel.class.getDeclaredField(n); f.setAccessible(true); return (ModelPart) f.get(m); } catch (Exception e) { return null; } };
                float d = 57.2958f;
                ModelPart hips = g.apply("hips"), body = g.apply("body"), chest = g.apply("chest"), neck = g.apply("neck"), head = g.apply("head"), t0 = g.apply("tail0");
                ModelPart[] arm = arr(m,"arm"), fore = arr(m,"fore");
                ModelPart[] th = arr(m,"thigh"), sh = arr(m,"shin"), ft = arr(m,"foot");
                float torso = (body.xRot + chest.xRot) * d;
                System.out.printf("%-12s %s torsoPitch=%5.1f neck=%5.1f head=%5.1f | arm=%5.1f fore=%5.1f | thigh=%5.1f shin=%5.1f foot=%5.1f | tail0=%5.1f hipsY=%4.1f%n", id, bip == 1 ? "biped" : "quad ", torso, neck.xRot * d, head.xRot * d, arm[0].xRot * d, fore[0].xRot * d, th[0].xRot * d, sh[0].xRot * d, ft[0].xRot * d, t0.xRot * d, hips.y);
            }
        }
    }
}
