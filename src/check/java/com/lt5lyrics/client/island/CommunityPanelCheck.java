package com.lt5lyrics.client.island;

import java.util.Random;

/** Headless layout properties; no Minecraft client, graphics or media session required. */
final class CommunityPanelCheck {
	private CommunityPanelCheck() {}

	static void run() throws Exception {
		// A click requests animated opening, and a transient blocked layout must not lose it.
		CommunityPanel state = new CommunityPanel();
		var open = CommunityPanel.class.getDeclaredField("open"); open.setAccessible(true);
		var revealField = CommunityPanel.class.getDeclaredField("reveal"); revealField.setAccessible(true);
		Anim reveal = (Anim) revealField.get(state);
		assert !open.getBoolean(state) && reveal.get() == 0.0F;
		state.showForIsland();
		assert open.getBoolean(state);
		reveal.update(1.0F);
		assert reveal.get() < 0.01F : "first frame begins closed rather than snapping";
		var started = Anim.class.getDeclaredField("start"); started.setAccessible(true);
		started.setLong(reveal, System.currentTimeMillis() - 100L);
		reveal.update(1.0F);
		assert reveal.get() > 0.5F && reveal.get() < 1.0F : "reveal animates over 200ms";
		state.pauseForMissingLayout();
		assert open.getBoolean(state) && reveal.get() == 0 : "space loss does not forget the open request";
		reveal.update(1.0F);
		assert reveal.get() < 0.01F : "reopening after space returns starts animated";
		state.closeForIsland();
		assert !open.getBoolean(state) : "outside dismissal closes with the island";
		state.showForIsland(); state.hide();
		assert !open.getBoolean(state) && reveal.get() == 0 : "leaving chat clears the card";
		assert !CommunityPanel.shouldCloseForOutside(10, 10, 160, 60, null, 50, 30);
		assert !CommunityPanel.shouldCloseForOutside(10, 10, 160, 60, new float[]{0,80,200,100}, 30, 100);
		assert CommunityPanel.shouldCloseForOutside(10, 10, 160, 60, new float[]{0,80,200,100}, 300, 200);

		var normal = CommunityPanel.layout(427, 240, 156.5F, 7, 114, 15, null);
		assert normal != null && normal.left() && normal.scale() == 1.0F;
		assert normal.card().w() == 110 && normal.card().h() == 164;
		assert Math.abs(normal.chip().x() + 16 + 6 - 156.5F) < 0.001F;
		assert normal.chip().y() == 7;

		var leftEdge = CommunityPanel.layout(427, 240, 4, 7, 114, 15, null);
		assert leftEdge != null && !leftEdge.left() : "use right when the left chip is off screen";
		var rightEdge = CommunityPanel.layout(427, 240, 309, 7, 114, 15, null);
		assert rightEdge != null && rightEdge.left() : "use left when the right chip is off screen";
		var blockedLeft = CommunityPanel.layout(427, 240, 156.5F, 7, 114, 15,
				new float[] {100, 2, 55, 200});
		assert blockedLeft != null && !blockedLeft.left() : "settings covering left chip moves it right";
		var shifted = CommunityPanel.layout(427, 400, 156.5F, 7, 114, 15,
				new float[] {10, 90, 120, 100});
		assert shifted != null && shifted.left() && shifted.card().y() >= 191
				: "card can move below an obstacle while remaining anchored to its side";
		// With an expanded idle island at the minimum common GUI width, prefer a large card below.
		var vertical = CommunityPanel.layout(320, 240, 76, 7, 168, 64, null);
		assert vertical != null && vertical.vertical() && vertical.below() && vertical.scale() >= 0.60F;
		assert vertical.card().y() >= 75 && vertical.card().y() + vertical.card().h() <= 225;
		var halfVertical = CommunityPanel.visibleCard(vertical, 0.5F);
		assert Math.abs(halfVertical.h() - vertical.card().h() / 2) < 0.01F;
		assert halfVertical.y() == vertical.card().y() : "below card reveals downward from the island";
		// If the lyric island is too tall for a card beneath, a compact side card is still visible.
		var compact = CommunityPanel.layout(320, 240, 76, 7, 168, 153, null);
		assert compact != null && !compact.vertical() && compact.scale() < 0.60F && compact.scale() >= 0.399F;
		var halfHorizontal = CommunityPanel.visibleCard(compact, 0.5F);
		assert Math.abs(halfHorizontal.w() - compact.card().w() / 2) < 0.01F;
		assert compact.left() && Math.abs(halfHorizontal.x() + halfHorizontal.w() - compact.card().x() - compact.card().w()) < 0.01F;
		var tiny = CommunityPanel.layout(210, 82, 70, 2, 100, 15, null);
		assert tiny == null : "hide rather than draw a clipped card below minimum scale";
		assert CommunityPanel.layout(427, 240, 156, 7, 114, 15,
				new float[] {0, 0, 427, 240}) == null : "hide if both chip locations are obstructed";

		Random r = new Random(0x5eed);
		int visible = 0, hidden = 0;
		for (int i = 0; i < 30000; i++) {
			float sw = 100 + r.nextInt(900), sh = 40 + r.nextInt(500);
			float iw = 40 + r.nextInt(180), ih = 12 + r.nextInt(180);
			float ix = -30 + r.nextInt((int) sw + 60), iy = r.nextInt((int) sh);
			float[] obstacle = r.nextBoolean() ? null : new float[] {
					r.nextInt((int) sw), r.nextInt((int) sh), r.nextInt(350), r.nextInt(280)};
			var l = CommunityPanel.layout(sw, sh, ix, iy, iw, ih, obstacle);
			if (l == null) { hidden++; continue; }
			visible++;
			var chip = l.chip(); var card = l.card();
			var island = new CommunityPanel.Rect(ix, iy, iw, ih);
			var settings = obstacle == null ? null : new CommunityPanel.Rect(obstacle[0], obstacle[1], obstacle[2], obstacle[3]);
			assert chip.onScreen(sw, sh) && card.onScreen(sw, sh);
			assert !chip.overlaps(island) && !card.overlaps(island) && !chip.overlaps(card);
			assert !chip.overlaps(settings) && !card.overlaps(settings);
			assert chip.w() == 16 && chip.h() == 16 && chip.y() == iy;
			assert l.scale() >= 0.399F && l.scale() <= 1.0F;
			assert Math.abs(card.w() - 110 * l.scale()) < 0.01F;
			assert Math.abs(card.h() - 164 * l.scale()) < 0.01F;
			if (l.left()) assert Math.abs(chip.x() + 16 + 6 - ix) < 0.01F;
			else assert Math.abs(chip.x() - (ix + iw + 6)) < 0.01F;
			if (l.vertical()) {
				assert card.y() + card.h() <= sh - 15 + 0.01F : "leave chat input clear";
				if (l.below()) assert card.y() >= iy + ih + 4 - 0.01F;
				else assert card.y() + card.h() <= iy - 4 + 0.01F;
			} else if (l.left()) assert Math.abs(card.x() + card.w() + 4 - chip.x()) < 0.01F;
			else assert Math.abs(card.x() - (chip.x() + 16 + 4)) < 0.01F;
		}
		assert visible > 1000 && hidden > 1000;
		System.out.println("PASS: community auto-open/reveal state, compact layouts and geometry (30,000 randomized cases)");
	}
}
