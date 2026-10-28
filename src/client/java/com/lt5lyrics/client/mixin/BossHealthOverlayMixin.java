package com.lt5lyrics.client.mixin;

import com.lt5lyrics.client.LT5LyricsClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lt5lyrics$hideWhenPlaying(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if(LT5LyricsClient.island() != null && LT5LyricsClient.island().shouldHideBossbar()) ci.cancel();
	}
}
