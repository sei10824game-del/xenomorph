import com.mojang.blaze3d.vertex.*;
import com.xenomorph.client.*;
import com.xenomorph.entity.*;
import net.minecraft.client.model.geom.ModelPart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class Preview {
    static class Collector implements VertexConsumer {
        List<float[]> v = new ArrayList<>(); float[] cur = new float[5];
        public VertexConsumer vertex(double x, double y, double z) { cur = new float[5]; cur[0]=(float)x; cur[1]=(float)y; cur[2]=(float)z; return this; }
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float w){cur[3]=u;cur[4]=w;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){v.add(cur);}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
    static BufferedImage tex;

    static BufferedImage render(List<float[]> verts, int W, int H, float yawDeg, float camDist, float lookY, float lookX) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) img.setRGB(x,y,0x7a8088);
        float[] zb = new float[W*H]; Arrays.fill(zb, 1e9f);
        double yaw = Math.toRadians(yawDeg); float cs=(float)Math.cos(yaw), sn=(float)Math.sin(yaw);
        // transform verts to camera space: rotate around y by yaw, then translate
        int n = verts.size()/4;
        float scale = H*0.9f;
        for (int q=0;q<n;q++) {
            float[][] p = new float[4][];
            for (int i=0;i<4;i++) {
                float[] s = verts.get(q*4+i);
                float x = s[0]-lookX, y = s[1]-lookY, z = s[2];
                float rx = x*cs + z*sn, rz = -x*sn + z*cs;
                p[i] = new float[]{rx, y, rz + camDist, s[3], s[4]};
            }
            // normal in camera space
            float ax=p[1][0]-p[0][0], ay=p[1][1]-p[0][1], az=p[1][2]-p[0][2];
            float bx=p[3][0]-p[0][0], by=p[3][1]-p[0][1], bz=p[3][2]-p[0][2];
            float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx; float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(nl<1e-9) continue; nx/=nl; ny/=nl; nz/=nl;
            float light = 0.5f + 0.5f*Math.max(0, (-nx*0.4f + ny*-0.5f - nz*0.75f)); // y is down in model->flipped later
            for (int tri=0; tri<2; tri++) {
                int[] idx = tri==0 ? new int[]{0,1,2} : new int[]{0,2,3};
                float[][] t = {p[idx[0]], p[idx[1]], p[idx[2]]};
                float[] sx = new float[3], sy = new float[3];
                for (int i=0;i<3;i++){ float zz=Math.max(0.1f,t[i][2]); sx[i]=W/2f + t[i][0]/zz*scale*1.0f; sy[i]=H/2f - t[i][1]/zz*scale; }
                float area=(sx[1]-sx[0])*(sy[2]-sy[0])-(sx[2]-sx[0])*(sy[1]-sy[0]);
                if (Math.abs(area)<1e-6) continue;
                int minx=Math.max(0,(int)Math.floor(Math.min(sx[0],Math.min(sx[1],sx[2])))), maxx=Math.min(W-1,(int)Math.ceil(Math.max(sx[0],Math.max(sx[1],sx[2]))));
                int miny=Math.max(0,(int)Math.floor(Math.min(sy[0],Math.min(sy[1],sy[2])))), maxy=Math.min(H-1,(int)Math.ceil(Math.max(sy[0],Math.max(sy[1],sy[2]))));
                for (int y=miny;y<=maxy;y++) for (int x=minx;x<=maxx;x++) {
                    float px=x+0.5f, py=y+0.5f;
                    float w0=((sx[1]-px)*(sy[2]-py)-(sx[2]-px)*(sy[1]-py))/area;
                    float w1=((sx[2]-px)*(sy[0]-py)-(sx[0]-px)*(sy[2]-py))/area;
                    float w2=1-w0-w1;
                    if (w0<-0.001||w1<-0.001||w2<-0.001) continue;
                    float z=w0*t[0][2]+w1*t[1][2]+w2*t[2][2];
                    if (z>=zb[y*W+x]) continue;
                    float u=w0*t[0][3]+w1*t[1][3]+w2*t[2][3], v=w0*t[0][4]+w1*t[1][4]+w2*t[2][4];
                    int tx=Math.floorMod((int)(u*tex.getWidth()), tex.getWidth()), ty=Math.floorMod((int)(v*tex.getHeight()), tex.getHeight());
                    int c=tex.getRGB(tx,ty);
                    int r=(int)(((c>>16)&255)*light), g=(int)(((c>>8)&255)*light), b=(int)((c&255)*light);
                    zb[y*W+x]=z; img.setRGB(x,y,(Math.min(255,r+8)<<16)|(Math.min(255,g+8)<<8)|Math.min(255,b+8));
                }
            }
        }
        return img;
    }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        // bake model
        int T = Integer.getInteger("T", 230);
        String spec = a[1]; // e.g. drone:biped:0:idle  | frames list separated by ','
        String[] frames = spec.split(",");
        BufferedImage sheet = new BufferedImage(T*Math.min(frames.length,5), T*((frames.length+4)/5), BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        for (int i=0;i<frames.length;i++) {
            String[] f = frames[i].split(":");
            Caste c = Caste.valueOf(f[0].toUpperCase());
            float bip = f[1].equals("biped")?1:0;
            Attack at = Attack.valueOf(f[2].toUpperCase());
            float t = Float.parseFloat(f[3]);
            float yaw = f.length>4?Float.parseFloat(f[4]):90;
            float limb = f.length>5?Float.parseFloat(f[5]):0, amt = f.length>6?Float.parseFloat(f[6]):0;
            tex = ImageIO.read(new File("/home/user/xenomorph/src/main/resources/assets/xenomorph/textures/entity/"+c.id+".png"));
            XenoModel m = new XenoModel(XenoModel.create(c).bakeRoot());
            m.pose(c, bip, at, t, limb, amt, 50f, 0, 0, Boolean.getBoolean("climb"), Boolean.getBoolean("hang"));
            PoseStack ps = new PoseStack();
            ps.scale(c.scale, c.scale, c.scale);
            ps.scale(-1,-1,1); ps.translate(0,-1.501,0);
            Collector col = new Collector();
            m.renderToBuffer(ps, col, 0, 0, 1,1,1,1);
            // model y: after scale(-1,-1,1)+translate, +y up is already world up
            BufferedImage im = render(col.v, T, T, yaw, Float.parseFloat(System.getProperty("z","1"))*3.5f*Math.max(1,c.scale*0.9f), Float.parseFloat(System.getProperty("ly","1"))*c.scale, Float.parseFloat(System.getProperty("lx","0")));
            g.drawImage(im, (i%5)*T, (i/5)*T, null);
            g.setColor(java.awt.Color.WHITE); g.drawString(frames[i], (i%5)*T+4, (i/5)*T+14);
        }
        ImageIO.write(sheet, "png", new File(out));
    }
}
