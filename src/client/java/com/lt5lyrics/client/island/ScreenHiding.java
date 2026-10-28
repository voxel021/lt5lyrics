package com.lt5lyrics.client.island;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Configurable visual suppression while a selected screen is open. */
public final class ScreenHiding {
    private ScreenHiding() {}
    public static boolean classify(boolean present, boolean studio, boolean chat, boolean inventory,
            boolean hideChat, boolean hideInventory, boolean hideOther) {
        if(!present || studio)return false;
        if(chat)return hideChat;
        if(inventory)return hideInventory;
        return hideOther;
    }
    public static boolean shouldHide(Screen screen) {
        return classify(screen!=null, screen instanceof LT5SettingsScreen || screen instanceof LT5PlusScreen || screen instanceof DragScreen,
            screen instanceof ChatScreen, screen instanceof AbstractContainerScreen<?>,
            PlusSettings.hideChat,PlusSettings.hideInventory,PlusSettings.hideOtherScreens);
    }
}
