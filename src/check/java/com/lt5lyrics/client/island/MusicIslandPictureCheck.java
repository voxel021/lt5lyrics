package com.lt5lyrics.client.island;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Resource identity, layout, visibility, alpha and passive-hit-area checks. */
final class MusicIslandPictureCheck {
    private static boolean overlaps(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
            && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    static void run() throws Exception {
        Path png = Path.of("src/main/resources/assets/lt5lyrics/textures/island/picture.png");
        BufferedImage image = ImageIO.read(png.toFile());
        assert image != null && image.getWidth() == 32 && image.getHeight() == 32;
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(png)));
        assert sha.equals("9352ba5afd2988a7ddd840541e82a8336072061ae418b01fd8491eeb1d1fca2a") : "must be the supplied PNG, unchanged";

        for (float x : new float[] {-500, 0, 137, 1920}) for (float y : new float[] {-25, 0, 80, 1000}) {
            float[] share = MusicIsland.shareBounds(x, y), pic = MusicIsland.pictureBounds(x, y);
            assert share[0] == x + 130 && share[1] == y + 11 && share[2] == 28 && share[3] == 10;
            assert pic[0] == x + 136 && pic[1] == y + 24 && pic[2] == 16 && pic[3] == 16;
            assert pic[2] / pic[3] == image.getWidth() / (float) image.getHeight() : "aspect ratio";
            assert !overlaps(share, pic) && pic[1] > share[1] + share[3];
            assert !overlaps(pic, new float[] {x + 24, y + 42, 120, 3}) : "progress bar";
            assert !overlaps(pic, new float[] {x + 46, y + 13, 78, 8}) : "title";
            assert !overlaps(pic, new float[] {x + 46, y + 23, 86, 8}) : "artist";
            assert !overlaps(pic, new float[] {x + 45, y + 40, 78, 14}) : "idle settings";
            assert pic[0] > x + 9 && pic[0] + pic[2] < x + 168;
        }
        assert (MusicIsland.pictureColor(1F) >>> 24) == 128;
        assert (MusicIsland.pictureColor(.5F) >>> 24) == 64;
        assert (MusicIsland.pictureColor(0F) >>> 24) == 0;
        assert MusicIsland.pictureVisible(true, 1F, false, false);
        assert !MusicIsland.pictureVisible(false, 1F, false, false) : "closed hides immediately";
        assert !MusicIsland.pictureVisible(true, 0F, false, false);
        assert !MusicIsland.pictureVisible(true, 1F, true, false) : "HUD drag screen";
        assert !MusicIsland.pictureVisible(true, 1F, false, true) : "full settings screen";
        System.out.println("PASS: exact picture resource, 50% alpha, expanded/closed lifecycle, aspect, layout and separated Share/control bounds");
    }
}
