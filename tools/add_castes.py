"""One-shot generator that appended the host-derived / aquatic / burster / Earth / crossover castes (rows, model specs, lang, loot, tags, eggs, textures).
Kept for reference; re-running is guarded by a marker comment in Caste.java."""
import re, json, os, sys, numpy as np
from PIL import Image
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
R = ROOT + '/src/main/resources/'
T = R + 'assets/xenomorph/textures/entity/'
J = ROOT + '/src/main/java/com/xenomorph/'
# id, enum, hp, dmg, spd, scale, stance, crest, tubes, extraArms, dreads, attacks, spec, (src texture, mul, add, overlay), en, ja
NEW = [
 ("gorilla_xeno","GORILLA_XENO",170,17,.30,2.0,2,3,"true","false","false","CLAW_L, CLAW_R, DOUBLE_CLAW, SLAM, STOMP, GRAB, BITE, GRAB_SLAM","p.style = 5; p.torso = 1.45f; p.limb = 1.3f; p.armK = 1.55f; p.legK = 0.8f; p.neckK = 0.5f; p.plates = 1; p.spines = 4; p.ribs = 3; p.domeL = 8; p.domeW = 6f; p.domeH = 5.4f; p.tailEnd = 0; p.tailSegs = 3;",("brute",(0.8,0.72,0.62),(12,6,2),"hair"),"Gorilla Xenomorph","ゴリラ・ゼノモーフ"),
 ("rhino_xeno","RHINO_XENO",230,20,.30,2.1,0,3,"false","false","false","CLAW_L, CLAW_R, CHARGE, HORN_TOSS, STOMP, SLAM, TAIL_SWEEP","p.style = 3; p.horns = 1; p.carapace = true; p.plates = 2; p.torso = 1.4f; p.limb = 1.3f; p.domeL = 7; p.domeW = 6.4f; p.domeH = 5.8f; p.spines = 3; p.tubeLen = 0; p.tailEnd = 1;",("crusher",(0.7,0.7,0.75),(18,18,22),"plates"),"Rhino Xenomorph","サイ・ゼノモーフ"),
 ("crocodile_xeno","CROCODILE_XENO",125,15,.28,1.7,0,0,"false","false","false","CLAW_L, CLAW_R, BITE, DEATH_ROLL, GRAB, POUNCE, TAIL_SWEEP","p.domeL = 11; p.domeW = 5f; p.domeH = 2.8f; p.mand = true; p.cheek = true; p.limb = 0.75f; p.torso = 1.1f; p.tailSegs = 9; p.tailT = 1.35f; p.spines = 9; p.ribs = 1; p.tubeLen = 0;",("prowler",(0.6,0.9,0.5),(4,24,6),"scaly"),"Crocodile Xenomorph","ワニ・ゼノモーフ"),
 ("scorpion_xeno","SCORPION_XENO",105,12,.34,1.6,0,1,"false","false","false","CLAW_L, CLAW_R, TAIL_STAB, GRAB, SPIT, TAIL_SWEEP","p.tailEnd = 1; p.tailT = 1.3f; p.tailSegs = 7; p.limb = 0.85f; p.clawL = 1.6f; p.armK = 1.4f; p.spines = 5; p.carapace = true; p.plates = 1; p.tubeLen = 0;",("sentinel",(1.2,0.95,0.55),(34,22,4),"plates"),"Scorpion Xenomorph","サソリ・ゼノモーフ"),
 ("snake_xeno","SNAKE_XENO",60,9,.40,1.2,0,0,"false","false","false","BITE, GRAB, DEATH_ROLL, POUNCE, TAIL_SWEEP, SPIT","p.limb = 0.5f; p.torso = 0.75f; p.tailSegs = 12; p.tailT = 0.9f; p.domeL = 11; p.domeW = 3.2f; p.mand = true; p.ribs = 0; p.spines = 2; p.tubeLen = 0; p.clawL = 0.6f;",("runner",(0.7,0.85,0.4),(8,20,4),"scaly"),"Snake Xenomorph","ヘビ・ゼノモーフ"),
 ("mantis_xeno","MANTIS_XENO",95,13,.38,1.5,1,1,"true","false","false","CLAW_L, CLAW_R, DOUBLE_CLAW, LEAP_SLASH, BLADE_FLURRY, BITE, TAIL_STAB","p.blade = true; p.elbow = true; p.limb = 0.8f; p.armK = 1.5f; p.clawL = 1.8f; p.neckK = 1.45f; p.upright = 0.15f; p.ribs = 5; p.spines = 2;",("lurker",(0.55,1.1,0.5),(4,30,4),"vines"),"Mantis Xenomorph","カマキリ・ゼノモーフ"),
 ("saurian_xeno","SAURIAN_XENO",115,13,.36,1.6,1,2,"true","false","false","CLAW_L, CLAW_R, BITE, POUNCE, TAIL_STAB, TAIL_SWEEP, LEAP_SLASH","p.domeL = 12; p.mand = true; p.cheek = true; p.limb = 1.0f; p.torso = 0.95f; p.tailSegs = 9; p.tailEnd = 2; p.spines = 6; p.upright = 0.1f;",("stalker",(0.9,0.8,0.55),(26,16,0),"scaly"),"Saurian Xenomorph","サウリアン・ゼノモーフ"),
 ("lionworm_xeno","LIONWORM_XENO",150,14,.30,1.8,0,0,"false","false","false","CLAW_L, CLAW_R, BITE, GRAB, DEATH_ROLL, POUNCE, TAIL_SWEEP","p.torso = 1.0f; p.limb = 0.8f; p.tailSegs = 12; p.tailT = 1.5f; p.domeL = 9; p.mand = true; p.cheek = true; p.spines = 8; p.ribs = 2; p.tubeLen = 0; p.dreads = true;",("prowler",(1.2,0.85,0.5),(30,14,0),"hair"),"Lion-Worm Xenomorph","ライオンワーム・ゼノモーフ"),
 ("jockey_xeno","JOCKEY_XENO",70,10,.45,1.3,1,1,"false","false","false","CLAW_L, CLAW_R, POUNCE, BITE, LEAP_SLASH, TAIL_STAB","p.limb = 0.75f; p.torso = 0.8f; p.clawL = 1.5f; p.armK = 1.3f; p.rider = true; p.tubeLen = 0; p.spines = 3;",("lurker",(1.0,0.8,0.75),(24,10,10),"patch"),"Jockey Xenomorph","ジョッキー・ゼノモーフ"),
 ("dolphin_xeno","DOLPHIN_XENO",90,11,.55,1.5,0,0,"false","false","false","BITE, POUNCE, TAIL_SWEEP, CLAW_L, CLAW_R","p.limb = 0.6f; p.torso = 1.0f; p.domeL = 13; p.domeW = 3.4f; p.mand = true; p.tailSegs = 7; p.tailEnd = 1; p.tubeLen = 0; p.spines = 3;",("runner",(0.55,0.75,1.0),(6,18,34),"ice"),"Dolphin Xenomorph","イルカ・ゼノモーフ"),
 ("shark_xeno","SHARK_XENO",140,16,.55,1.8,0,1,"false","false","false","BITE, DEATH_ROLL, POUNCE, TAIL_SWEEP, GRAB","p.limb = 0.55f; p.torso = 1.2f; p.domeL = 11; p.domeW = 4.8f; p.mand = true; p.cheek = true; p.tailSegs = 7; p.tailEnd = 2; p.tubeLen = 0; p.spines = 6;",("prowler",(0.65,0.7,0.85),(10,14,26),"scaly"),"Shark Xenomorph","サメ・ゼノモーフ"),
 ("beluga_xeno","BELUGA_XENO",130,12,.45,1.8,0,0,"false","false","false","BITE, POUNCE, TAIL_SWEEP, SLAM, CLAW_L, CLAW_R","p.limb = 0.6f; p.torso = 1.25f; p.domeL = 9; p.domeW = 5.4f; p.domeH = 5.5f; p.tailSegs = 6; p.tubeLen = 0; p.spines = 2;",("winterbeast",(1.0,1.0,1.1),(24,24,28),"frost"),"Beluga Xenomorph","シロイルカ・ゼノモーフ"),
 ("swimmer_xeno","SWIMMER_XENO",75,9,.5,1.3,0,0,"false","false","false","BITE, POUNCE, TAIL_SWEEP, CLAW_L, CLAW_R","p.limb = 0.55f; p.torso = 0.85f; p.tailSegs = 8; p.tailT = 1.1f; p.domeL = 12; p.tubeLen = 0; p.spines = 4; p.ribs = 2;",("runner",(0.7,0.9,0.9),(4,24,26),"scaly"),"Swimming Xenomorph","遊泳型ゼノモーフ"),
 ("giant_burster","GIANT_BURSTER",110,12,.28,1.9,0,0,"false","false","false","CLAW_L, CLAW_R, BITE, GRAB, SLAM, TAIL_SWEEP","p.limb = 0.8f; p.torso = 1.3f; p.domeL = 7; p.tubeLen = 0; p.ribs = 1; p.spines = 2; p.bare = true; p.tailSegs = 5;",("burster",(1.0,1.0,0.9),(20,10,0),"cells"),"Giant Chestburster","巨大チェストバスター"),
 ("multiburster","MULTIBURSTER",70,8,.36,1.4,0,0,"false","false","false","CLAW_L, CLAW_R, BITE, POUNCE, TAIL_SWEEP","p.limb = 0.7f; p.torso = 0.95f; p.tubeLen = 0; p.ribs = 4; p.spines = 5; p.sac = true; p.tailSegs = 5;",("burster",(0.9,1.0,0.9),(12,20,6),"cells"),"Multiburster","マルチバースター"),
 ("bodyburster","BODYBURSTER",60,10,.34,1.3,0,0,"false","false","false","CLAW_L, CLAW_R, BITE, DETONATE","p.limb = 0.75f; p.torso = 1.05f; p.tubeLen = 0; p.ribs = 5; p.spines = 3; p.glowHead = true; p.style = 4; p.sac = true;",("burster",(1.0,0.9,0.8),(26,6,0),"cells"),"Bodyburster","ボディバースター"),
 ("queen_burster","QUEEN_BURSTER",200,15,.28,2.0,0,2,"false","false","false","CLAW_L, CLAW_R, BITE, GRAB, SLAM, TAIL_STAB, LAY, SUMMON","p.style = 2; p.fan = 11; p.limb = 0.85f; p.torso = 1.2f; p.domeL = 9; p.tubeLen = 0; p.ribs = 2; p.spines = 4; p.sac = true; p.tailSegs = 6;",("queen",(1.0,0.95,0.9),(30,20,14),"patch"),"Queen Chestburster","クイーン・バースター"),
 ("abomination","ABOMINATION",200,19,.34,2.0,1,5,"true","false","true","CLAW_L, CLAW_R, DOUBLE_CLAW, BITE, GRAB, STOMP, TAIL_STAB, TAIL_SWEEP, FRENZY","p.dreads = true; p.mand = true; p.cheek = true; p.brow = true; p.torso = 1.4f; p.limb = 1.25f; p.armK = 1.2f; p.plates = 1; p.spines = 6; p.ribs = 5; p.clawL = 1.4f; p.domeL = 9; p.domeW = 5.6f; p.domeH = 5.4f; p.tailEnd = 3;",("predalien",(0.8,0.7,0.65),(10,4,0),"muscle"),"Abomination","アボミネーション"),
 ("larva","LARVA",12,3,.30,0.55,0,0,"false","false","false","BITE, CLAW_L, CLAW_R","p.limb = 0.5f; p.torso = 0.65f; p.tailSegs = 4; p.tubeLen = 0; p.domeL = 7; p.ribs = 2; p.spines = 0; p.clawL = 0.7f;",("burster",(1.1,1.05,0.95),(34,26,14),"cells"),"Larva","ラーバ"),
 ("earth_xeno","EARTH_XENO",55,8,.38,1.15,1,1,"true","false","false","CLAW_L, CLAW_R, DOUBLE_CLAW, TAIL_STAB, TAIL_SWEEP, BITE, POUNCE","p.ribs = 5; p.spines = 3; p.limb = 0.95f; p.torso = 0.9f; p.clawL = 1.3f; p.cheek = true;",("warrior",(0.75,0.85,0.8),(4,10,10),"gloss"),"Earth Xenomorph","アース・ゼノモーフ"),
 ("necromorph_xeno","NECROMORPH_XENO",120,15,.34,1.7,1,0,"false","true","false","CLAW_L, CLAW_R, DOUBLE_CLAW, BLADE_FLURRY, TAIL_STAB, BITE, GRAB, FLAIL","p.blade = true; p.elbow = true; p.knee = true; p.limb = 0.9f; p.torso = 1.0f; p.armK = 1.45f; p.ribs = 7; p.spines = 8; p.bare = true; p.clawL = 1.7f; p.tubeLen = 0;",("lurker",(1.1,0.85,0.7),(34,18,6),"muscle"),"Necromorph Xenomorph","ネクロモーフ・ゼノモーフ"),
 ("terminator_xeno","TERMINATOR_XENO",180,18,.30,1.9,2,2,"true","false","false","CLAW_L, CLAW_R, DOUBLE_CLAW, FIREARM, SLAM, BITE, TAIL_STAB","p.carapace = true; p.plates = 2; p.elbow = true; p.knee = true; p.torso = 1.25f; p.limb = 1.15f; p.ribs = 4; p.spines = 3; p.cheek = true; p.brow = true; p.clawL = 1.3f;",("warrior",(1.1,1.1,1.2),(40,40,46),"hex"),"Terminator Xenomorph","ターミネーター・ゼノモーフ"),
]
def main():
    cj = J + 'entity/Caste.java'; s = open(cj).read()
    if 'GORILLA_XENO' in s: print('already added'); return
    rows = ',\n'.join(f'    {e} ("{i}", {hp}, {dmg}, {spd}f, {sc}f, {st}, {cr}, {tu}, {ar}, {dr}, EnumSet.of({at}))' for (i,e,hp,dmg,spd,sc,st,cr,tu,ar,dr,at,*_) in NEW)
    k = s.index('JUNGLE_XENO ("jungle_xeno"'); end = s.index(';', s.index('))', k)); 
    s = s[:end] + ',\n    // --- host-derived, aquatic, burster, Alien: Earth and crossover castes\n' + rows + s[end:]
    s = s.replace('    public boolean cloneStrain()', '    /** Lives in water: swims, suffocates on land. */\n    public boolean aquatic() { return this == DOLPHIN_XENO || this == SHARK_XENO || this == BELUGA_XENO || this == SWIMMER_XENO; }\n    public boolean cloneStrain()', 1)
    open(cj, 'w').write(s)
    mj = J + 'client/XenoModel.java'; m = open(mj).read()
    cases = ''.join(f'            case {e} -> {{ {sp} }}\n' for (i,e,*_,sp,tex,en,ja) in [(x[0],x[1])+x[2:] for x in NEW] ) if False else ''
    for (i,e,hp,dmg,spd,sc,st,cr,tu,ar,dr,at,sp,tex,en,ja) in NEW: cases += f'            case {e} -> {{ {sp} }}\n'
    anchor = '            case DRONE -> { p.spines = 1; }'
    m = m.replace(anchor, cases + anchor, 1); open(mj, 'w').write(m)
    for f, idx in (('en_us', 14), ('ja_jp', 15)):
        q = R + f'assets/xenomorph/lang/{f}.json'; d = json.load(open(q))
        for x in NEW:
            i = x[0]; name = x[idx]
            d[f'entity.xenomorph.{i}'] = name; d[f'item.xenomorph.{i}_spawn_egg'] = name + (' Spawn Egg' if f == 'en_us' else ' スポーンエッグ')
        json.dump(d, open(q, 'w'), ensure_ascii=False, indent=1)
    tg = R + 'data/xenomorph/tags/entity_types/xenomorphs.json'; d = json.load(open(tg)); d['values'] += [f'xenomorph:{x[0]}' for x in NEW]; json.dump(d, open(tg, 'w'))
    for x in NEW:
        i = x[0]
        json.dump({"parent": "minecraft:item/template_spawn_egg"}, open(R + f'assets/xenomorph/models/item/{i}_spawn_egg.json', 'w'))
        json.dump({"type": "minecraft:entity", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "xenomorph:resin", "functions": [{"function": "minecraft:set_count", "count": {"min": 0, "max": 2}}, {"function": "minecraft:looting_enchant", "count": {"min": 0, "max": 1}}]}]}, {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance_with_looting", "chance": 0.2, "looting_multiplier": 0.08}], "entries": [{"type": "minecraft:item", "name": "xenomorph:acid_gland"}]}]}, open(R + f'data/xenomorph/loot_tables/entities/{i}.json', 'w'))
    # textures: recolour an existing caste then apply the lineage overlay
    src = open(ROOT + '/tools/retex_mutants.py').read(); prefix = src[:src.index('for cid,(src,mul,add,kind) in spec.items():')]
    prefix = prefix.replace("T='/home/user/xenomorph/src/main/resources/assets/xenomorph/textures/entity/'", f"T='{T}'")
    g = {}; exec(prefix, g)
    for x in NEW:
        i = x[0]; srcp, mul, add, kind = x[13]
        im = np.asarray(Image.open(T + srcp + '.png').convert('RGBA'), dtype=float)
        keep = im[144:192, :, :3].copy()
        rgb = im[..., :3] * np.array(mul) + np.array(add)
        rgb = g['overlay'](kind if kind in ('muscle','bone','cells','void','ice','wither','lava','hex','sand','hair','frost','vines','patch') else 'cells' if kind=='scaly' else 'patch', rgb)
        rgb[144:192] = np.clip(keep * np.array(mul) + np.array(add), 0, 255)
        Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), im[..., 3:]], axis=2).astype('uint8'), 'RGBA').save(T + i + '.png')
    print('added', len(NEW))
if __name__ == '__main__': main()
