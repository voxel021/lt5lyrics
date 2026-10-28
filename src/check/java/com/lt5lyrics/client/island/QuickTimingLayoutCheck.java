package com.lt5lyrics.client.island;

/** Geometry and hit areas for the new quick controls, including compact GUI windows. */
final class QuickTimingLayoutCheck {
    private QuickTimingLayoutCheck() {}
    private static boolean overlaps(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
            && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    static void run() {
        assert QuickTimingLayout.expandedHeight(true, 240) == 153;
        assert QuickTimingLayout.expandedHeight(false, 240) == 105;
        assert QuickTimingLayout.lyricHeight(153) == 44 : "normal lyric list retains its previous space";
        for (float screen : new float[] {90, 120, 180, 240, 360, 720}) {
            for (boolean lyrics : new boolean[] {true, false}) {
                float h = QuickTimingLayout.expandedHeight(lyrics, screen);
                assert h <= screen - 4;
                float x = 10, y = 2;
                float[] picture = MusicIsland.pictureBounds(x, y);
                float[] share = MusicIsland.shareBounds(x, y);
                float[] header = {x + 10, y + QuickTimingLayout.top(h), 148, 5};
                float[] playback = {x + 10, y + h - 24, 148, 16};
                float[] lyric = {x + 12, y + 56, 144, QuickTimingLayout.lyricHeight(h)};
                assert !overlaps(header, picture) && !overlaps(header, share);
                assert header[1] >= picture[1] + picture[3] + 4 : "allow for text glyph overhang";
                assert !overlaps(header, playback);
                if (lyric[3] > 0) assert lyric[1] + lyric[3] <= header[1] - 4;
                for (int i=0; i<5; i++) {
                    float[] b = QuickTimingLayout.button(x, y, h, i);
                    assert b[3] >= 7 : "clickable even at 90-pixel GUI height";
                    assert b[0] >= x && b[0] + b[2] <= x + 168;
                    assert b[1] + b[3] <= y + h - 26;
                    assert !overlaps(b, picture) && !overlaps(b, share) && !overlaps(b, playback);
                    if (lyric[3] > 0) assert !overlaps(b, lyric);
                    for (int j=0; j<i; j++) assert !overlaps(b, QuickTimingLayout.button(x, y, h, j));
                    double mx = b[0]+b[2]/2, my = b[1]+b[3]/2;
                    assert QuickTimingLayout.hitButton(x, y, h, mx, my) == i;
                    assert QuickTimingLayout.inArea(x, y, h, mx, my);
                }
                assert QuickTimingLayout.hitButton(x, y, h, x, y) == -1;
                if (QuickTimingLayout.showProgress(h)) assert QuickTimingLayout.top(h) >= 54;
            }
        }
        assert !QuickTimingLayout.showProgress(86) : "compact mode makes room instead of overlapping controls";
        assert QuickTimingLayout.showProgress(105);
        System.out.println("PASS: quick controls and hit areas preserve Share, picture, lyrics and playback at normal and compact sizes");
    }
}
