import com.xenomorph.client.*;
import com.xenomorph.entity.*;
import net.minecraft.client.model.geom.ModelPart;
import java.lang.reflect.*;
import java.util.*;

public class UvDump {
    static Field fCubes, fChildren, fPolys, fVerts, fU, fV;
    static StringBuilder sb = new StringBuilder();
    static void walk(String path, ModelPart p, boolean[] first) throws Exception {
        List<?> cubes = (List<?>) fCubes.get(p);
        if (!cubes.isEmpty()) {
            if (!first[0]) sb.append(','); first[0] = false;
            sb.append('"').append(path).append("\":[");
            boolean f2 = true;
            for (Object cube : cubes) {
                float u0 = 9, v0 = 9, u1 = -9, v1 = -9;
                Object[] polys = (Object[]) fPolys.get(cube);
                for (Object poly : polys) for (Object vx : (Object[]) fVerts.get(poly)) {
                    float u = fU.getFloat(vx), v = fV.getFloat(vx);
                    u0 = Math.min(u0, u); u1 = Math.max(u1, u); v0 = Math.min(v0, v); v1 = Math.max(v1, v);
                }
                if (!f2) sb.append(','); f2 = false;
                sb.append('[').append(Math.round(u0 * 512)).append(',').append(Math.round(v0 * 256)).append(',').append(Math.round(u1 * 512)).append(',').append(Math.round(v1 * 256)).append(']');
            }
            sb.append(']');
        }
        Map<String, ModelPart> ch = (Map<String, ModelPart>) fChildren.get(p);
        for (var e : ch.entrySet()) walk(path.isEmpty() ? e.getKey() : path + "/" + e.getKey(), e.getValue(), first);
    }
    public static void main(String[] a) throws Exception {
        fCubes = ModelPart.class.getDeclaredField("cubes"); fCubes.setAccessible(true);
        fChildren = ModelPart.class.getDeclaredField("children"); fChildren.setAccessible(true);
        Class<?> cube = Class.forName("net.minecraft.client.model.geom.ModelPart$Cube");
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        fPolys = cube.getDeclaredField("polygons"); fPolys.setAccessible(true);
        fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        Class<?> mv = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        Class<?> tv = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        fU = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex").getDeclaredField("u"); fU.setAccessible(true);
        fV = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex").getDeclaredField("v"); fV.setAccessible(true);
        StringBuilder all = new StringBuilder("{");
        boolean f = true;
        for (String id : a) {
            Caste c = Caste.valueOf(id.toUpperCase());
            sb = new StringBuilder("{");
            walk("", XenoModel.create(c).bakeRoot(), new boolean[]{true});
            sb.append('}');
            if (!f) all.append(','); f = false;
            all.append('"').append(c.id).append("\":").append(sb);
        }
        all.append('}');
        java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/scratch/uvmap.json"), all.toString());
        System.out.println("ok");
    }
}
