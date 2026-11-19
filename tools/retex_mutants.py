import numpy as np, sys
from PIL import Image, ImageFilter
T='/home/user/xenomorph/src/main/resources/assets/xenomorph/textures/entity/'
rng=np.random.default_rng(1234)
H,W=256,512
def noise(scale,seed):
    r=np.random.default_rng(seed).random((H,W))
    im=Image.fromarray((r*255).astype('uint8')).filter(ImageFilter.GaussianBlur(scale))
    a=np.asarray(im,dtype=float); a=(a-a.min())/(a.max()-a.min()+1e-9); return a
yy,xx=np.mgrid[0:H,0:W]
SKIP=slice(144,192)   # cranium strip keeps its sheen
# (id, source texture, mul, add, overlay name)
spec={
 "hulk_xeno":("brute",(0.7,1.05,0.6),(8,30,6),"muscle"),
 "bone_xeno":("sentinel",(1.3,1.25,1.1),(38,34,24),"bone"),
 "blast_xeno":("burster",(0.55,1.0,0.5),(0,22,4),"cells"),
 "void_xeno":("lurker",(1.1,0.75,1.9),(22,0,50),"void"),
 "frost_xeno":("winterbeast",(0.8,1.1,1.35),(8,28,52),"ice"),
 "wither_xeno":("lurker",(0.8,0.8,0.9),(18,18,26),"wither"),
 "blaze_xeno":("scorched_xeno",(1.3,0.85,0.4),(30,8,0),"lava"),
 "shulker_xeno":("armored_xeno",(1.0,0.6,1.1),(40,10,46),"hex"),
 "husk_xeno":("crusher",(1.2,1.05,0.75),(46,34,8),"sand"),
 "hog_xeno":("bull_alien",(1.0,0.78,0.62),(26,8,2),"hair"),
 "frozen_xeno":("winterbeast",(0.85,1.05,1.25),(4,30,56),"frost"),
 "jungle_xeno":("prowler",(0.6,1.1,0.55),(4,34,4),"vines"),
 "chimera_queen":("queen",(0.9,0.6,1.2),(24,0,36),"patch"),
}
def overlay(kind,rgb):
    n1,n2,n3=noise(1.5,1),noise(4,2),noise(9,3)
    if kind=="muscle":
        f=0.8+0.4*np.sin(yy*0.9+n2*6)*0.5+0.4*n1; rgb*=f[...,None]
        scar=(np.abs(np.sin(xx*0.21+yy*0.35+n3*9))<0.05)*60; rgb+=scar[...,None]*np.array([1,0.8,0.7])
    elif kind=="bone":
        grain=np.sin(yy*0.5+n3*10)*0.5+0.5; rgb*=(0.85+0.2*grain)[...,None]
        crack=(np.abs(n2-0.5)<0.012)*1.0; rgb*=(1-0.6*crack)[...,None]
        rgb[...,2]+=10*n1
    elif kind=="cells":
        cell=np.abs(np.sin(xx*0.45)*np.sin(yy*0.55)); rgb*=(0.7+0.5*cell+0.2*n2)[...,None]; rgb[...,1]+=22*cell
    elif kind=="void":
        rgb*=(0.9+0.5*n2)[...,None]
        star=(np.random.default_rng(9).random((H,W))>0.994)*200; rgb+=star[...,None]*np.array([0.8,0.4,1.0])
        vein=(np.abs(n3-0.5)<0.02)*160; rgb+=vein[...,None]*np.array([0.55,0.15,1.0])
    elif kind=="ice":
        facet=np.abs(np.sin((xx+yy)*0.35+n2*5)); rgb*=(0.8+0.45*facet)[...,None]; rgb+=((n1>0.8)*70)[...,None]
    elif kind=="wither":
        plate=((np.sin(xx*0.18)*np.sin(yy*0.22))>0.3)*28; rgb+=plate[...,None]
        crack=(np.abs(n2-0.5)<0.02)*90; rgb+=crack[...,None]*np.array([1,0.2,0.3])
    elif kind=="lava":
        rgb*=(0.7+0.5*n2)[...,None]; vein=(np.abs(n3-0.5)<0.03); rgb[vein]=rgb[vein]*0.4+np.array([255,120,20])*0.6
    elif kind=="hex":
        hexp=np.abs(np.sin(xx*0.4+yy*0.23))*np.abs(np.sin(xx*0.4-yy*0.23)); rgb*=(0.7+0.6*hexp)[...,None]
    elif kind=="sand":
        rgb*=(0.85+0.3*n1)[...,None]; crack=(np.abs(n2-0.5)<0.015); rgb[crack]*=0.45; rgb[...,0]+=12*n3
    elif kind=="hair":
        streak=np.random.default_rng(5).random((1,W)); hair=np.sin(xx*0.9+n2*6)*0.5+0.5; rgb*=(0.7+0.5*hair*(0.6+0.4*streak))[...,None]
    elif kind=="frost":
        rgb*=(0.85+0.25*n2)[...,None]; rgb+=((n1>0.78)*80)[...,None]; rgb[...,2]+=14*n3
    elif kind=="vines":
        vine=(np.abs(np.sin(xx*0.12+yy*0.3+n2*5))<0.08); rgb[vine]=rgb[vine]*0.5+np.array([30,140,30])*0.5; rgb*=(0.85+0.3*n1)[...,None]
    elif kind=="patch":
        pid=(np.floor(xx/48)+np.floor(yy/40)*7)%5
        tint=np.array([[1.0,1.2,0.9],[1.2,1.2,1.1],[1.1,0.9,1.3],[0.9,1.1,1.0],[1.0,0.8,0.9]])[pid.astype(int)]
        rgb*=tint*(0.85+0.3*n2)[...,None]
    return rgb
for cid,(src,mul,add,kind) in spec.items():
    im=np.asarray(Image.open(T+src+'.png').convert('RGBA'),dtype=float)
    keep=im[SKIP,:,:3].copy()
    rgb=im[...,:3]*np.array(mul)+np.array(add)
    rgb=overlay(kind,rgb)
    rgb[SKIP]=np.clip(keep*np.array(mul)+np.array(add),0,255)
    Image.fromarray(np.concatenate([np.clip(rgb,0,255),im[...,3:]],axis=2).astype('uint8'),'RGBA').save(T+cid+'.png')
print('ok')
