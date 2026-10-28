package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.Font;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Deterministic fixed-only ground-spawn regressions; never needs a live world. */
final class FixedGroundPlacementCheck {
    static void run() {
        reproducedCurvedFallback();
        horizontalYawNotPitch();
        partialCollisionShapes();
        obstructionAndLockedSites();
        curvedGlyphBounds();
        randomizedSafeCenters();
        multilineFloorAndStableAnchor();
        animationScaleMargin();
        System.out.println("PASS: fixed 3D pitch, wall/terrain site selection, bounded curve/ground geometry, immutable anchors and locked pins");
    }

    private static void reproducedCurvedFallback() {
        Vec3 feet = new Vec3(0, 64, 0), eye = feet.add(0, 1.62, 0);
        WorldLyrics.Placed node = new WorldLyrics.Placed("fixture", new Vec3(-3.5, 64, 6),
            new Vec3(-1,0,0), new Vec3(0,1,0), .035F, null, 100);
        node.normalManaged = true;
        // Typical curved, wrapped text bounds: the original 12-step fallback
        // can push this ordinary group many blocks into the sky.
        WorldLyrics.FixedGroundBounds bounds = new WorldLyrics.FixedGroundBounds(1.60, 2.0, 3.0, .08);
        double center = WorldLyrics.fixedNormalGroundCenter(node, feet.y, Double.NEGATIVE_INFINITY, bounds,
            null, eye, IslandSettings.HologramBillboard.SMART_BTP);
        System.out.println("Curved fixed placement reproduction: feet=" + feet.y + ", center=" + center);
        assert center - feet.y < 3.0 : "curved fixed spawn is unexpectedly high: " + center;
    }

    private static void horizontalYawNotPitch() {
        for (Vec3 feet : List.of(new Vec3(1.25, 64, -33.75), new Vec3(-70.5, -60, 200), new Vec3(0, 155.5, 0))) {
            for (float yaw : new float[] {0, 45, 90, 180, -90, -135}) {
                double yr = Math.toRadians(yaw);
                for (double side : new double[] {-9, -1.25, 1.25, 3.5, 9}) {
                    for (double distance : new double[] {2, 6, 24}) {
                        Vec3 expected = null;
                        for (int pitch : new int[] {-90, -75, -35, 0, 35, 75, 90}) {
                            double pr = Math.toRadians(pitch);
                            Vec3 view = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
                            Vec3 anchor = WorldLyrics.computeFixedNormalAnchor(feet, view, yaw, distance, side);
                            Vec3 right = WorldLyrics.normalHorizontalRight(view, yaw);
                            Vec3 forward = new Vec3(right.z, 0, -right.x);
                            Vec3 delta = anchor.subtract(feet);
                            assert Math.abs(anchor.y - feet.y) < 1e-10 : "pitch may never put a fixed spawn below the feet";
                            assert Math.abs(delta.dot(forward) - distance) < 1e-8 : "horizontal distance";
                            assert Math.abs(delta.dot(right) - side) < 1e-8 : "signed side/offset";
                            if (expected == null) expected = anchor;
                            else assert anchor.distanceTo(expected) < 1e-8 : "up/down pitch changes neither the floor nor horizontal placement";
                        }
                    }
                }
            }
        }
        Vec3 feet = new Vec3(0, 64, 0), eye = feet.add(0, 1.62, 0), down = new Vec3(0, -1, 0);
        assert WorldLyrics.computeNormalAnchor(eye, down, 0, 6, 3.5, .45).y < feet.y : "reproduce previous bug";
        assert WorldLyrics.computeFixedNormalAnchor(feet, down, 0, 6, 3.5).y == feet.y : "fixed placement no longer uses pitch vertically";
        Vec3 fallback = WorldLyrics.computeFixedNormalAnchor(feet, Vec3.ZERO, 90, 6, 3.5);
        assert Double.isFinite(fallback.x) && Double.isFinite(fallback.z) && fallback.y == feet.y;
    }

    private static void partialCollisionShapes() {
        BlockPos pos = new BlockPos(-4, 70, 12);
        AABB full = new AABB(0,0,0, 1,1,1);
        AABB slab = new AABB(0,0,0, 1,.5,1);
        AABB half = new AABB(0,0,0, .5,1,1);
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.5, 70, 12.5, List.of(full)) == 71;
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.5, 70, 12.5, List.of(slab)) == 70.5;
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.5, 70.75, 12.5, List.of(slab)) == 70.75 : "air above bottom slab is not a full block";
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.25, 70, 12.5, List.of(half)) == 70 : "outside shape stays at feet";
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.75, 70.25, 12.5, List.of(half, slab)) == 71 : "tallest overlapping box";
        assert WorldLyrics.fixedNormalCollisionTop(pos, -3.5, 70, 12.5, List.of()) == 70;
    }

    private static void obstructionAndLockedSites() {
        AABB full = new AABB(0, 0, 0, 1, 1, 1), slab = new AABB(0, 0, 0, 1, .5, 1);
        Vec3 feet = new Vec3(0, 64, 0), forward = new Vec3(0,0,1), right = new Vec3(-1,0,0);
        Vec3 nominal = WorldLyrics.computeFixedNormalAnchor(feet, forward, 0, 6, 3.5);
        BlockPos col = BlockPos.containing(nominal);
        var empty = (WorldLyrics.FixedNormalCollisionLookup) pos -> List.of();
        assert WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, empty).equals(nominal);
        var fullStep = (WorldLyrics.FixedNormalCollisionLookup) pos -> pos.getY() == 64 ? List.of(full) : List.of();
        var slabStep = (WorldLyrics.FixedNormalCollisionLookup) pos -> pos.getY() == 64 ? List.of(slab) : List.of();
        Vec3 onFull = WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, fullStep);
        Vec3 onSlab = WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, slabStep);
        assert onFull.x == nominal.x && onFull.z == nominal.z && Math.abs(onFull.y - 65.00001) < 1e-9;
        assert onSlab.x == nominal.x && onSlab.z == nominal.z && Math.abs(onSlab.y - 64.50001) < 1e-9;

        // A tall tree/wall in the requested column used to be climbed up to eight blocks.
        var tallColumn = (WorldLyrics.FixedNormalCollisionLookup) pos ->
            pos.getX() == col.getX() && pos.getZ() == col.getZ() && pos.getY() >= 64 && pos.getY() < 80
                ? List.of(full) : List.of();
        assert Double.isNaN(WorldLyrics.fixedNormalFloor(nominal.x, nominal.z, feet.y, tallColumn));
        Vec3 around = WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, tallColumn);
        assert around.y == feet.y : around;
        assert Math.hypot(around.x-nominal.x, around.z-nominal.z) <= WorldLyrics.FIXED_NORMAL_SEARCH_RADIUS;
        assert around.x != nominal.x || around.z != nominal.z : "search an unobstructed nearby spawn site";
        assert around.subtract(feet).dot(right) >= IslandSettings.MIN_NORMAL_ABS_OFFSET-1e-8;
        assert around.subtract(feet).dot(forward) >= IslandSettings.MIN_NORMAL_DISTANCE-1e-8;
        assert WorldLyrics.fixedNormalFloor(around.x, around.z, feet.y, tallColumn) == feet.y;

        Vec3 locked = WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, true, tallColumn);
        assert locked.equals(nominal) : "never shift a locked/reused X/Z around an obstacle";
        var solidEverywhere = (WorldLyrics.FixedNormalCollisionLookup) pos ->
            pos.getY() >= 64 && pos.getY() < 90 ? List.of(full) : List.of();
        Vec3 noOpenSite = WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, solidEverywhere);
        assert noOpenSite.equals(nominal) : "stay at feet if the whole search radius is blocked";
        int[] probes = {0};
        var unloaded = (WorldLyrics.FixedNormalCollisionLookup) pos -> { probes[0]++; return null; };
        assert WorldLyrics.fixedNormalSpawnSite(nominal, feet, right, 1, false, unloaded).equals(nominal);
        assert probes[0] == 1 : "don't load chunks or hunt around an unloaded nominal location";

        // Fractional feet, negative Y, both sides, all yaw and steep pitch share the bounded rule.
        for (Vec3 player : List.of(new Vec3(.1, 64.7, -.25), new Vec3(-3.75, -59.5, 10))) {
            for (int yaw = -180; yaw <= 180; yaw += 30) for (int pitch : new int[] {-90,-45,0,45,90}) {
                double yr=Math.toRadians(yaw), pr=Math.toRadians(pitch);
                Vec3 view=new Vec3(-Math.sin(yr)*Math.cos(pr), -Math.sin(pr), Math.cos(yr)*Math.cos(pr));
                Vec3 r=WorldLyrics.normalHorizontalRight(view, yaw), f=new Vec3(r.z,0,-r.x);
                for (int sign : new int[] {-1,1}) for (double distance : new double[] {2,6,24}) {
                    Vec3 preferred=WorldLyrics.computeFixedNormalAnchor(player,view,yaw,distance,sign*1.25);
                    BlockPos block=BlockPos.containing(preferred);
                    var obstacle=(WorldLyrics.FixedNormalCollisionLookup) pos ->
                        pos.getX()==block.getX() && pos.getZ()==block.getZ()
                          && pos.getY() >= (int)Math.floor(player.y) && pos.getY() < (int)Math.floor(player.y)+12
                          ? List.of(full) : List.of();
                    Vec3 site=WorldLyrics.fixedNormalSpawnSite(preferred,player,r,sign,false,obstacle);
                    assert site.y >= player.y && site.y <= player.y+WorldLyrics.FIXED_NORMAL_MAX_STEP_UP+2e-5;
                    assert Math.hypot(site.x-preferred.x,site.z-preferred.z) <= WorldLyrics.FIXED_NORMAL_SEARCH_RADIUS+1e-8;
                    Vec3 delta=site.subtract(player);
                    assert sign*delta.dot(r) >= IslandSettings.MIN_NORMAL_ABS_OFFSET-1e-8;
                    assert delta.dot(f) >= IslandSettings.MIN_NORMAL_DISTANCE-1e-8;
                }
            }
        }
    }

    private static void curvedGlyphBounds() {
        IslandSettings.resetAllSettingsToFactory();
        try {
            Font font=new FakeFont();
            Vec3 floorAnchor=new Vec3(-3.5,64,6), eye=new Vec3(0,65.62,0);
            String line="one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen";
            String translation="one two three four five six seven eight nine ten eleven twelve";
            for (boolean curved : new boolean[] {false,true}) for (float curve : new float[] {.3F,.8F,2F})
            for (float global : new float[] {.5F,1F,2F}) for (float size : new float[] {.5F,1F,2F}) {
                IslandSettings.normalWordsPerLine=3;
                IslandSettings.normalCurveEnabled=curved; IslandSettings.normalCurve=curve;
                IslandSettings.hologramScale=global; IslandSettings.lyricTextScale=1F; IslandSettings.normalTextSize=size;
                WorldLyrics.Placed p=new WorldLyrics.Placed(line,translation,floorAnchor,new Vec3(-1,0,0),new Vec3(0,1,0),.035F,null,100);
                p.normalManaged=true;
                WorldLyrics.NormalRows rows=WorldLyrics.buildNormalRows(font,p);
                WorldLyrics.FixedGroundBounds bounds=WorldLyrics.fixedNormalGroundBounds(font,p,rows);
                double base=p.scale*global*size;
                for (double anim : new double[] {.65, 1.0, 1.08, 1.16}) {
                    double scale=base*anim;
                    double halfW=(rows.halfWidthLocal()+3.4)*scale;
                    double radius=WorldLyrics.normalCurveRadius(curved,curve,halfW);
                    if (Double.isFinite(radius)) {
                        double angle=Math.min(1.05,halfW/radius);
                        double plateDepth=radius*(1-Math.cos(angle))-.08*Math.max(.025,scale*24);
                        assert plateDepth <= bounds.curveDepth()+1e-7;
                        assert plateDepth >= -bounds.backDepth()-1e-7;
                    }
                    for (WorldLyrics.NormalRow row: rows.rows()) for (float[] q: row.glyphs) for (int flip: new int[] {-1,1}) {
                        double glyphScale=scale*row.scaleFactor;
                        double center=(q[0]+q[10])*.5*glyphScale*flip;
                        for (int v=0;v<4;v++) {
                            double x=q[v*5]*glyphScale*flip;
                            double y=-q[v*5+1]*glyphScale-row.baselineDown*scale;
                            assert Math.abs(y)+font.getSize()*.14*glyphScale+.58*glyphScale <= bounds.halfHeight()+1e-5;
                            double[] actual=WorldLyrics.normalCurveOffsets(x,center,radius);
                            assert Math.abs(actual[0]) <= bounds.halfWidth()+1e-5;
                            assert actual[1] <= bounds.curveDepth()+1e-6 : "curve depth " + actual[1] + " > " + bounds;
                            assert actual[1] >= -bounds.backDepth()-1e-6;
                            double[] shadow=WorldLyrics.normalCurveOffsets(x+.58*glyphScale*flip,center,radius);
                            double shadowDepth=shadow[1]-.004*Math.max(.025,glyphScale*24);
                            assert shadowDepth <= bounds.curveDepth()+1e-6;
                            assert shadowDepth >= -bounds.backDepth()-1e-6;
                        }
                    }
                }
                Quaternionf rotation=new Quaternionf().rotateY(.8F).rotateX(-.6F).rotateZ(.25F);
                for (var mode : IslandSettings.HologramBillboard.values()) {
                    double center=WorldLyrics.fixedNormalGroundCenter(p,64,Double.NEGATIVE_INFINITY,bounds,rotation,eye,mode);
                    p.anchor=new Vec3(floorAnchor.x,center,floorAnchor.z);
                    Vec3[] basis=WorldLyrics.computeHologramBasis(p,rotation,eye,mode);
                    float flip=WorldLyrics.textFlip(basis[0],basis[1],eye.subtract(p.anchor));
                    Vec3 facing=basis[0].cross(basis[1]).normalize().scale(flip);
                    assert WorldLyrics.fixedNormalBottomBound(center,bounds,basis[0],basis[1],facing) >= 64.025-1e-7;
                    p.anchor=floorAnchor;
                }
            }
        } finally { IslandSettings.resetAllSettingsToFactory(); }
    }

    private static void randomizedSafeCenters() {
        java.util.Random rng=new java.util.Random(1116);
        for (int n=0;n<3000;n++) {
            double floor=-64+rng.nextDouble()*320;
            double halfH=.05+rng.nextDouble()*8, halfW=.05+rng.nextDouble()*12;
            double depth=rng.nextDouble()*15, back=rng.nextDouble()*2;
            WorldLyrics.FixedGroundBounds bounds=new WorldLyrics.FixedGroundBounds(halfH,halfW,depth,back);
            Vec3 original=new Vec3(-3.5,floor,6), camera=new Vec3(rng.nextDouble()*8-4,floor+rng.nextDouble()*10-4,rng.nextDouble()*5-2);
            WorldLyrics.Placed p=new WorldLyrics.Placed("test",original,new Vec3(-1,0,0),new Vec3(0,1,0),.035F,null,100);
            p.normalManaged=true;
            Quaternionf rotation=new Quaternionf().rotateY((float)(rng.nextDouble()*6-3)).rotateX((float)(rng.nextDouble()*3-1.5)).rotateZ((float)(rng.nextDouble()*.6-.3));
            IslandSettings.HologramBillboard mode=IslandSettings.HologramBillboard.values()[n%IslandSettings.HologramBillboard.values().length];
            double minimum = n%3==0 ? floor+rng.nextDouble()*20 : Double.NEGATIVE_INFINITY;
            double center=WorldLyrics.fixedNormalGroundCenter(p,floor,minimum,bounds,rotation,camera,mode);
            assert p.anchor.equals(original) : "solver must not move an existing group";
            assert Double.isFinite(center) && center>=floor+halfH+.025-1e-8 && center>=minimum;
            p.anchor=new Vec3(original.x,center,original.z);
            Vec3[] basis=WorldLyrics.computeHologramBasis(p,rotation,camera,mode);
            float flip=WorldLyrics.textFlip(basis[0],basis[1],camera.subtract(p.anchor));
            Vec3 facing=basis[0].cross(basis[1]).normalize().scale(flip);
            double bottom=WorldLyrics.fixedNormalBottomBound(center,bounds,basis[0],basis[1],facing);
            assert bottom>=floor+.025-1e-7 : "bounded floor failure " + mode + " " + bottom + " vs " + floor;
        }
    }

    private static void multilineFloorAndStableAnchor() {
        IslandSettings.resetAllSettingsToFactory();
        try {
            IslandSettings.normalWordsPerLine = 2;
            IslandSettings.showTranslations = true;
            Font font = new FakeFont();
            Vec3 floorAnchor = new Vec3(-3.5, 64, 6);
            WorldLyrics.Placed p = new WorldLyrics.Placed("one two three four five six seven eight", "un deux trois quatre cinq six", floorAnchor,
                new Vec3(-1,0,0), new Vec3(0,1,0), .035F, null, 100);
            p.normalManaged = true;
            WorldLyrics.NormalRows rows = WorldLyrics.buildNormalRows(font, p);
            assert rows.rows().size() >= 6 : "main and translation must be measured before placing";
            for (float size : new float[] {.5F, 1F, 2F}) for (boolean curve : new boolean[] {false, true}) {
                IslandSettings.normalTextSize = size;
                IslandSettings.normalCurveEnabled = curve;
                WorldLyrics.FixedGroundBounds bounds = WorldLyrics.fixedNormalGroundBounds(font, p, rows);
                for (Vec3 camera : List.of(new Vec3(0,65.62,0), new Vec3(0,63,0), new Vec3(8,80,10))) {
                    for (IslandSettings.HologramBillboard mode : IslandSettings.HologramBillboard.values()) {
                        Quaternionf rotation = new Quaternionf().rotateY(.7F).rotateX(.8F).rotateZ(.15F);
                        Vec3 before = p.anchor;
                        double y = WorldLyrics.fixedNormalGroundCenter(p, 64, Double.NEGATIVE_INFINITY, bounds, rotation, camera, mode);
                        assert p.anchor.equals(before) : "the ground calculation is pure for the saved group";
                        assert Double.isFinite(y) && y > 64 : "center is never below the feet";
                        p.anchor = new Vec3(floorAnchor.x, y, floorAnchor.z);
                        Vec3[] basis = WorldLyrics.computeHologramBasis(p, rotation, camera, mode);
                        float flip = WorldLyrics.textFlip(basis[0], basis[1], camera.subtract(p.anchor));
                        Vec3 facing = basis[0].cross(basis[1]).normalize().scale(flip);
                        double bottom = WorldLyrics.fixedNormalBottomBound(y, bounds, basis[0], basis[1], facing);
                        assert bottom >= 64.025 - 1e-7 : "initial visible bounds include all wrapped/translated rows: " + bottom + " " + mode;
                        Vec3 saved = p.anchor;
                        for (int f=0; f<30; f++) {
                            WorldLyrics.advanceNormalFollow(p, false, camera.add(f, f*.5, -f), Vec3.ZERO, .05F);
                            assert p.anchor.equals(saved) : "fixed lines are never repositioned after spawn";
                        }
                        p.anchor = floorAnchor;
                        double locked = WorldLyrics.fixedNormalGroundCenter(p, 64, 90, bounds, rotation, camera, mode);
                        assert locked >= 90 : "a locked reused position never lowers its existing center";
                    }
                }
            }
        } finally { IslandSettings.resetAllSettingsToFactory(); }
    }

    private static void animationScaleMargin() {
        for (IslandSettings.LyricAnimation anim : IslandSettings.LyricAnimation.values()) {
            for (long ms=0; ms<=3000; ms+=5) {
                float s = LyricAnimator.computeActive(anim, ms, 1).scaleFactor();
                assert s * 1.035F <= 1.16F : "ground bounds account for pop and beat scale";
            }
        }
    }

    private static final class FakeFont extends Font {
        FakeFont() { super(null, 10); }
        @Override public float width(String text) { return text.codePointCount(0, text.length()) * 5F; }
        @Override public List<float[]> buildGlyphs(String text, float x, float baseline, List<Integer> indices) {
            List<float[]> out = new ArrayList<>();
            for (int i=0; i<text.length();) {
                int cp=text.codePointAt(i);
                if (!Character.isWhitespace(cp)) {
                    float[] q=new float[20];
                    q[0]=x; q[1]=-8; q[5]=x; q[6]=2; q[10]=x+4; q[11]=2; q[15]=x+4; q[16]=-8;
                    out.add(q); if (indices != null) indices.add(i);
                }
                x+=5; i+=Character.charCount(cp);
            }
            return out;
        }
    }
    private FixedGroundPlacementCheck() {}
}
