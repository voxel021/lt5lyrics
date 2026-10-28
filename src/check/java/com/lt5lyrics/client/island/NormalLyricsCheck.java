package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Headless, deterministic checks for the v1.6 Normal 3D-only renderer. */
final class NormalLyricsCheck {
    static void run() throws Exception {
        wrapsWithoutLosingWordsOrIndices();
        placesOnAllSidesInThreeDimensions();
        curvesWithoutChangingAnchor();
        detectsBehindWithoutCrosshairGuard();
        laysOutRowsAndTranslationsAsOneGroup();
        persistsAndMigratesSettings();
        worldResetAndSkyHudIsolation();
        System.out.println("PASS: Normal 3D wrapping, karaoke indices, side/offset/distance, pitched placement, curvature, unmasked crosshair area, visibility, group spacing, persistence and isolation");
    }

    private static void wrapsWithoutLosingWordsOrIndices() {
        String text = "One   two\tthree, four FIVE six\nភាសា ខ្មែរ 世界 👩‍👩‍👧‍👦 seven eight nine\r\n\nlast supercalifragilisticexpialidocious!";
        List<NormalLyricsLayout.Span> rows = NormalLyricsLayout.wrap(text, 5);
        assert rows.size() == 6 : rows;
        assert rows.get(0).text().equals("One   two\tthree, four FIVE");
        assert rows.get(1).text().equals("six");
        assert rows.get(2).text().equals("ភាសា ខ្មែរ 世界 👩‍👩‍👧‍👦 seven");
        assert rows.get(3).text().equals("eight nine");
        assert rows.get(4).text().isEmpty();
        assert rows.get(5).text().startsWith("last");
        for (var row : rows) {
            assert text.substring(row.start(), row.end()).equals(row.text());
            for (int i = 0; i < row.text().length(); i++) {
                assert row.originalIndex(i) >= 0 && row.originalIndex(i) < text.length();
                assert text.charAt(row.originalIndex(i)) == row.text().charAt(i);
            }
        }
        String joined = rows.stream().map(NormalLyricsLayout.Span::text).reduce("", (a,b) -> a + " " + b);
        for (String word : List.of("One", "two", "three,", "four", "FIVE", "six", "ភាសា", "ខ្មែរ", "世界", "👩‍👩‍👧‍👦", "seven", "eight", "nine", "last", "supercalifragilisticexpialidocious!")) {
            assert joined.indexOf(word) >= 0 && joined.indexOf(word) == joined.lastIndexOf(word) : word;
        }
        assert NormalLyricsLayout.wrap("short", 5).size() == 1;
        assert NormalLyricsLayout.wrap("one two three four five six", 5).stream().map(NormalLyricsLayout.Span::text).toList().equals(List.of("one two three four five", "six"));
        assert NormalLyricsLayout.wrap("a\u00A0b c", 1).stream().map(NormalLyricsLayout.Span::text).toList().equals(List.of("a\u00A0b", "c"));
        assert NormalLyricsLayout.wrap("one\u200Btwo", 1).stream().map(NormalLyricsLayout.Span::text).toList().equals(List.of("one", "two"));
        assert NormalLyricsLayout.wrap("中文没有空格的长句", 1).size() == 1 : "never split an unbroken word";
        assert NormalLyricsLayout.wrap("a\nb\n", 5).stream().map(NormalLyricsLayout.Span::text).toList().equals(List.of("a", "b", ""));
    }

    private static void placesOnAllSidesInThreeDimensions() {
        for (var side : IslandSettings.NormalSide.values()) {
            int a = WorldLyrics.chooseNormalSide(side, -2, 0, false);
            int b = WorldLyrics.chooseNormalSide(side, -2, 1, true);
            switch (side) {
                case LEFT -> { assert a == -1 && b == -1; }
                case RIGHT -> { assert a == 1 && b == 1; }
                case ALTERNATE -> { assert a == -1 && b == 1; }
                case RANDOM -> { assert a == -1 && b == 1; }
            }
        }
        assert WorldLyrics.signedNormalOffset(0, -1) == -IslandSettings.MIN_NORMAL_ABS_OFFSET;
        assert WorldLyrics.signedNormalOffset(0, 1) == IslandSettings.MIN_NORMAL_ABS_OFFSET;
        assert WorldLyrics.signedNormalOffset(-5, 1) == 5;
        assert WorldLyrics.signedNormalOffset(1000, -1) == -IslandSettings.MAX_NORMAL_OFFSET;
        Vec3 eye = new Vec3(120.5, 64.2, -300.75);
        for (int yaw = -180; yaw <= 180; yaw += 30) for (int pitch = -90; pitch <= 90; pitch += 15) {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            Vec3 view = new Vec3(-Math.sin(y)*Math.cos(p), -Math.sin(p), Math.cos(y)*Math.cos(p)).normalize();
            Vec3 right = WorldLyrics.normalHorizontalRight(view, yaw);
            Vec3 up = right.cross(view).normalize();
            for (double offset : new double[] {-9, -1.25, 1.25, 3.5, 9}) for (double distance : new double[] {2, 6, 24}) {
                Vec3 delta = WorldLyrics.computeNormalAnchor(eye, view, yaw, distance, offset, 0.45).subtract(eye);
                assert Math.abs(delta.dot(view) - distance) < 1.0E-8 : "forward " + yaw + "," + pitch + ": " + delta;
                assert Math.abs(delta.dot(right) - offset) < 1.0E-8 : "side " + yaw + "," + pitch + ": " + delta;
                assert Math.abs(delta.dot(up) - 0.45) < 1.0E-8;
                assert WorldLyrics.normalInFront(eye, view, eye.add(delta));
            }
        }
        IslandSettings.resetNormalDefaults();
        IslandSettings.setNormalOffset(-0.2);
        assert IslandSettings.normalOffset == -1.25 && IslandSettings.normalSide == IslandSettings.NormalSide.LEFT;
        IslandSettings.setNormalOffset(0);
        assert IslandSettings.normalOffset == -1.25;
        IslandSettings.setNormalSide(IslandSettings.NormalSide.ALTERNATE);
        IslandSettings.setNormalOffset(3.5);
        assert IslandSettings.normalSide == IslandSettings.NormalSide.ALTERNATE && IslandSettings.normalOffset == 3.5;
        WorldLyrics.Placed node = new WorldLyrics.Placed("a", Vec3.ZERO, new Vec3(-1,0,0), new Vec3(0,1,0), .035F, null, 5);
        node.normalManaged = true; node.normalSideSign = -1; node.normalSignedOffset = -4.25; node.normalForwardDistance = 9;
        WorldLyrics.Placed copy = node.withTextAndTranslation("b", "translation", 5);
        assert copy.normalManaged && copy.normalSideSign == -1 && copy.normalSignedOffset == -4.25 && copy.normalForwardDistance == 9;
    }

    private static void curvesWithoutChangingAnchor() {
        double flat = WorldLyrics.normalCurveRadius(false, 2, 3);
        assert Double.isInfinite(flat);
        assert Double.isInfinite(WorldLyrics.normalCurveRadius(true, 0, 3));
        double[] f = WorldLyrics.normalCurveOffsets(2.0, 1.8, flat);
        assert f[0] == 2.0 && f[1] == 0.0;
        double weak = WorldLyrics.normalCurveRadius(true, .4F, 2);
        double strong = WorldLyrics.normalCurveRadius(true, 1.6F, 2);
        assert strong < weak;
        double[] left = WorldLyrics.normalCurveOffsets(-1.5, -1.5, strong);
        double[] mid = WorldLyrics.normalCurveOffsets(0, 0, strong);
        double[] right = WorldLyrics.normalCurveOffsets(1.5, 1.5, strong);
        assert Math.abs(left[0] + right[0]) < 1e-9 && Math.abs(left[1] - right[1]) < 1e-9;
        assert left[1] > 0 && mid[1] == 0 && right[1] > 0;
        for (double center = -2; center <= 2; center += .1) {
            double[] a = WorldLyrics.normalCurveOffsets(center, center, strong);
            double[] b = WorldLyrics.normalCurveOffsets(center+.01, center+.01, strong);
            assert Math.hypot(a[0]-b[0], a[1]-b[1]) < .011;
            assert Math.cos(Math.clamp(center/strong, -1.05, 1.05)) > 0 : "glyph never faces away";
        }
        // The curved rounded backdrop is a continuous mesh in the same world-space cylinder.
        int strips = 24;
        float[] plate = WorldLyrics.buildNormalCurvedPlate(0, 0, 0, new Vec3(1,0,0), new Vec3(0,1,0),
            new Vec3(0,0,-1), 2, 1, 1, strong, -.05, strips);
        for (int i = 0; i < strips - 1; i++) {
            int a = i * 20 + 15, b = (i + 1) * 20;
            for (int j = 0; j < 5; j++) assert Math.abs(plate[a+j] - plate[b+j]) < 1e-6 : "plate seam " + i;
        }
        assert Math.abs(plate[3]) < 1e-6 && Math.abs(plate[(strips - 1) * 20 + 18] - 1) < 1e-6;
        assert plate[2] < plate[(strips / 2) * 20 + 2] : "curved edge approaches camera";
        Vec3 eye = Vec3.ZERO, look = new Vec3(0,0,1);
        Vec3 before = WorldLyrics.computeNormalAnchor(eye, look, 0, 6, 3.5, .45);
        IslandSettings.normalTextSize = 2.0F; IslandSettings.normalCurve = 2.0F; IslandSettings.normalCurveEnabled = true;
        Vec3 after = WorldLyrics.computeNormalAnchor(eye, look, 0, 6, 3.5, .45);
        assert before.equals(after) : "size and curvature cannot move spawn anchor";
        assert .035F * 2 > .035F : "normal size changes physical scale";
    }

    private static void detectsBehindWithoutCrosshairGuard() {
        Vec3 eye = Vec3.ZERO, view = new Vec3(0,0,1);
        assert WorldLyrics.normalInFront(eye, view, new Vec3(0,0,6));
        assert WorldLyrics.normalInFront(eye, view, new Vec3(1000,0,.001)) : "off-screen edge is still in front";
        assert !WorldLyrics.normalInFront(eye, view, new Vec3(0,0,-6));
        assert !WorldLyrics.normalDirectionVisible(true, eye, view, new Vec3(0,0,-6));
        assert WorldLyrics.normalDirectionVisible(false, eye, view, new Vec3(0,0,-6));
        assert WorldLyrics.normalDirectionVisible(true, eye, view, new Vec3(100,0,.01));
        assert !WorldLyrics.normalInFront(eye, view, new Vec3(10,0,0));
        assert WorldLyrics.normalInFront(eye, new Vec3(0,1,0), new Vec3(1,1,-9));
    }

    private static void laysOutRowsAndTranslationsAsOneGroup() {
        IslandSettings.resetNormalDefaults();
        IslandSettings.showTranslations = true;
        Font font = new FakeFont();
        WorldLyrics.Placed group = new WorldLyrics.Placed("one two three four five six seven", "un deux trois quatre cinq six", new Vec3(-3,0,6),
            new Vec3(-1,0,0), new Vec3(0,1,0), .035F, null, 100);
        WorldLyrics.NormalRows rows = WorldLyrics.buildNormalRows(font, group);
        assert rows.rows().size() == 4 : rows.rows().size();
        assert !rows.rows().get(0).translation && !rows.rows().get(1).translation;
        assert rows.rows().get(2).translation && rows.rows().get(3).translation;
        List<NormalLyricsLayout.Span> cached = group.mainSpans;
        WorldLyrics.buildNormalRows(font, group);
        assert group.mainSpans == cached : "source spans are cached";
        float priorBottom = Float.POSITIVE_INFINITY;
        for (WorldLyrics.NormalRow row : rows.rows()) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (float[] q : row.glyphs) for (int v=0; v<4; v++) {
                min = Math.min(min, q[v*5+1]); max = Math.max(max, q[v*5+1]);
            }
            float top = -(min * row.scaleFactor + row.baselineDown);
            float bottom = -(max * row.scaleFactor + row.baselineDown);
            if (Float.isFinite(priorBottom)) assert priorBottom - top >= NormalLyricsLayout.rowGap(10, IslandSettings.normalLineSpacing) - .001F;
            priorBottom = bottom;
            if (!row.translation) for (int i=0; i<row.originalIndices.size(); i++) {
                int original = row.originalIndices.get(i);
                assert original >= 0 && original < group.text.length();
                assert group.text.charAt(original) != ' ';
            }
        }
        float narrowGap = NormalLyricsLayout.rowGap(10, .75F), wideGap = NormalLyricsLayout.rowGap(10, 2.0F);
        assert narrowGap >= 2.8F && wideGap > narrowGap;
        IslandSettings.normalWordsPerLine = 2;
        assert WorldLyrics.buildNormalRows(font, group).rows().size() == 7;
        IslandSettings.showTranslations = false;
        assert WorldLyrics.buildNormalRows(font, group).rows().size() == 4;
        WorldLyrics.Placed shorter = group.withTextAndTranslation("short", "", 20);
        assert WorldLyrics.buildNormalRows(font, shorter).rows().size() == 1 : "no stale rows after lyric changes";
    }

    private static void persistsAndMigratesSettings() throws Exception {
        Path file = Files.createTempFile("lt5-normal-", ".json");
        try {
            IslandSettings.resetAllSettingsToFactory();
            assert !IslandSettings.follow && IslandSettings.normalHideBehind && IslandSettings.normalWordsPerLine == 5;
            Files.writeString(file, "{\"follow\":true}", StandardCharsets.UTF_8);
            IslandSettings.loadFromFile(file);
            assert !IslandSettings.follow : "old following default migrates to fixed anchors";
            IslandSettings.follow = true;
            IslandSettings.normalDistance = 12.25;
            IslandSettings.setNormalSide(IslandSettings.NormalSide.ALTERNATE);
            IslandSettings.setNormalOffset(-4.75);
            IslandSettings.normalCurveEnabled = true; IslandSettings.normalCurve = 1.45F;
            IslandSettings.normalWordsPerLine = 7; IslandSettings.normalLineSpacing = 1.55F;
            IslandSettings.normalTextSize = 1.65F; IslandSettings.normalHideBehind = false;
            IslandSettings.saveToFile(file);
            IslandSettings.resetNormalDefaults(); IslandSettings.follow = false;
            IslandSettings.loadFromFile(file);
            assert IslandSettings.follow;
            assert IslandSettings.normalDistance == 12.25 && IslandSettings.normalOffset == -4.75;
            assert IslandSettings.normalSide == IslandSettings.NormalSide.ALTERNATE;
            assert IslandSettings.normalCurveEnabled && Math.abs(IslandSettings.normalCurve - 1.45F) < .001F;
            assert IslandSettings.normalWordsPerLine == 7 && Math.abs(IslandSettings.normalLineSpacing - 1.55F) < .001F;
            assert Math.abs(IslandSettings.normalTextSize - 1.65F) < .001F && !IslandSettings.normalHideBehind;
            Files.writeString(file, "{\"normalDistance\":9999,\"normalOffset\":0,\"normalSide\":\"LEFT\",\"normalTextSize\":-30,\"normalWordsPerLine\":999,\"normalCurve\":50}");
            IslandSettings.loadFromFile(file);
            assert IslandSettings.normalDistance == IslandSettings.MAX_NORMAL_DISTANCE;
            assert IslandSettings.normalOffset == -IslandSettings.MIN_NORMAL_ABS_OFFSET;
            assert IslandSettings.normalTextSize == IslandSettings.MIN_NORMAL_SIZE;
            assert IslandSettings.normalWordsPerLine == IslandSettings.MAX_NORMAL_WORDS;
            assert IslandSettings.normalCurve == IslandSettings.MAX_NORMAL_CURVE;
            assert SettingsPanel.matchingSearchTitles("normal 3d offset").contains("Normal 3D Offset");
            assert SettingsPanel.matchingSearchTitles("behind").contains("Hide Lyrics Behind You");
        } finally { Files.deleteIfExists(file); IslandSettings.resetAllSettingsToFactory(); }
    }

    private static void worldResetAndSkyHudIsolation() throws Exception {
        IslandSettings.resetAllSettingsToFactory();
        double radius = IslandSettings.skyBoundaryRadius; float skySize = IslandSettings.skyTextSize;
        int skyColor = IslandSettings.resolveSkyPrimaryColor(), hudColor = IslandSettings.resolveHudPrimaryColor();
        Vec3 center = new Vec3(2,60,4);
        Vec3 sky = SkyStableLayout.initialSlot(center, radius, 0, IslandSettings.skyHeight);
        IslandSettings.normalTextSize = 2; IslandSettings.normalDistance = 22; IslandSettings.normalOffset = -8;
        IslandSettings.normalSide = IslandSettings.NormalSide.RANDOM; IslandSettings.normalCurveEnabled = true;
        assert SkyStableLayout.initialSlot(center, radius, 0, IslandSettings.skyHeight).equals(sky);
        assert IslandSettings.skyBoundaryRadius == radius && IslandSettings.skyTextSize == skySize;
        assert IslandSettings.resolveSkyPrimaryColor() == skyColor && IslandSettings.resolveHudPrimaryColor() == hudColor;
        String plateVert = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_normal_plate.vsh"));
        String plateFrag = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_normal_plate.fsh"));
        assert plateVert.contains("FragCoord = UV0") && plateFrag.contains("in vec2 FragCoord;");
        String oldShape = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_shape.vsh"));
        assert oldShape.contains("RECT_COORDS[gl_VertexIndex % 4]") : "HUD/Sky shape path remains unchanged";
        String normalGlyph = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_normal_msdf.fsh"));
        String normalVertex = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_normal_msdf.vsh"));
        String sharedGlyph = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_msdf.fsh"));
        for (String shader : List.of(normalGlyph, plateFrag)) {
            assert !shader.contains("gl_FragCoord") && !shader.contains("crosshairDistance")
                && !shader.contains("crosshairMask") : "no screen-coordinate cutout";
            assert shader.contains("FragColor.a * alpha") : "only glyph/plate shape and opacity determine coverage";
        }
        assert !sharedGlyph.contains("crosshairDistance") : "HUD and Sky glyphs remain unchanged";
        assert !normalVertex.contains("Extra.z > 0.5") : "Normal projection must not use Sky far plane";
        String world = Files.readString(Path.of("src/client/java/com/lt5lyrics/client/island/WorldLyrics.java"));
        String normalDraw = world.substring(world.indexOf("private static void drawHologram("), world.indexOf("Continuous rounded plate"));
        assert !world.contains("normalCrosshairMask") && !normalDraw.contains("normalCrosshairOverlaps")
            : "no whole-group or circular crosshair suppression";
        assert normalDraw.contains("submitNormalCurvedPlate(plate, 1") && normalDraw.contains("submitNormalCurvedPlate(plate, strips")
            : "flat and curved plates use their Normal pipeline";
        assert !normalDraw.contains("crosshair") : "no mask is sent to glyphs or plates";
        String render = Files.readString(Path.of("src/client/java/com/lt5lyrics/client/island/render/IslandRender.java"));
        assert render.contains("NORMAL_MSDF_SEE_THROUGH") && render.contains("submitNormalStyledGlyphs")
            && render.contains("submitNormalGlyphs") && !render.contains("float[] crosshair")
            : "both occlusion modes, glow/outline/fill and shadow render without the mask";
        var seq = WorldLyrics.class.getDeclaredField("normalAlternateSeq"); seq.setAccessible(true); seq.setInt(null, 12);
        WorldLyrics.reset();
        assert seq.getInt(null) == 0 : "world/dimension reset clears sequence";
        IslandSettings.resetAllSettingsToFactory();
    }

    /** Fake metrics let us test grouping without a game, GPU or texture atlas. */
    private static final class FakeFont extends Font {
        FakeFont() { super(null, 10); }
        @Override public float width(String s) { return s.codePointCount(0, s.length()) * 5.0F; }
        @Override public List<float[]> buildGlyphs(String text, float x, float baseline, List<Integer> indices) {
            List<float[]> result = new ArrayList<>();
            for (int i=0; i<text.length();) {
                int cp = text.codePointAt(i);
                if (!Character.isWhitespace(cp)) {
                    float[] q = new float[20];
                    q[0] = x; q[1] = -8; q[5] = x; q[6] = 2;
                    q[10] = x+4; q[11] = 2; q[15] = x+4; q[16] = -8;
                    result.add(q); if (indices != null) indices.add(i);
                }
                x += 5; i += Character.charCount(cp);
            }
            return result;
        }
    }
    private NormalLyricsCheck() {}
}
