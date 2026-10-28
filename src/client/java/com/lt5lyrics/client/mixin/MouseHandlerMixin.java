package com.lt5lyrics.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.lt5lyrics.client.LT5LyricsClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
	private void lt5lyrics$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if(window != mc.getWindow().handle()) return;
		// World/Sky lyric holograms are display-only: gameplay clicks pass to Minecraft.
		// The music bar/settings still handle their own clicks when appropriate.
		if(LT5LyricsClient.island() != null){
			MouseHandler self = (MouseHandler) (Object) this;
			double x = self.getScaledXPos(mc.getWindow());
			double y = self.getScaledYPos(mc.getWindow());
			if(action == InputConstants.PRESS && LT5LyricsClient.island().handleClick(x, y, info.button())){
				ci.cancel();
			} else if(action == InputConstants.RELEASE && LT5LyricsClient.island().handleMouseRelease(x, y, info.button())){
				ci.cancel();
			}
		}
	}

	@Inject(method = "onMove(JDDDD)V", at = @At("HEAD"), cancellable = true)
	private void lt5lyrics$onMove(long window, double xpos, double ypos, double deltaX, double deltaY, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if(window != mc.getWindow().handle()) return;
		if(LT5LyricsClient.island() != null){
			double scaledX = MouseHandler.getScaledXPos(mc.getWindow(), xpos);
			double scaledY = MouseHandler.getScaledYPos(mc.getWindow(), ypos);
			if(LT5LyricsClient.island().handleMouseMove(scaledX, scaledY)){
				ci.cancel();
			}
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lt5lyrics$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if(window != mc.getWindow().handle()) return;
		// Do not route the wheel to world lyric holograms either.
		if(LT5LyricsClient.island() != null){
			MouseHandler self = (MouseHandler) (Object) this;
			double x = self.getScaledXPos(mc.getWindow());
			double y = self.getScaledYPos(mc.getWindow());
			if(LT5LyricsClient.island().handleScroll(x, y, vertical)) ci.cancel();
		}
	}
}
