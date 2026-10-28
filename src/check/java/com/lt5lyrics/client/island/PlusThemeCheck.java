package com.lt5lyrics.client.island;

import java.nio.file.Files;
import java.nio.file.Path;

/** Responsive layout and theme contracts; actual interaction is also tested in a client. */
public final class PlusThemeCheck {
    private PlusThemeCheck() {}

    public static void run() throws Exception {
        int[][] screens = {{640,360,1},{320,180,2},{427,240,2},{640,360,2},
                           {284,160,3},{160,120,3},{160,90,3},{220,115,2}};
        for (int[] screen : screens) {
            PlusLayout.Bounds b=PlusLayout.forScreen(screen[0],screen[1],screen[2]);
            assert b.panelX()>=0 && b.panelY()>=0;
            assert b.panelX()+b.panelWidth()<=screen[0];
            assert b.panelY()+b.panelHeight()<=screen[1];
            assert b.bodyTop()>=b.sectionY() && b.bodyBottom()<=b.footerY();
            assert b.bodyHeight()>=b.buttonHeight() : "At least one control must be visible " + b;
            assert b.contentWidth()>0 && b.tabHeight()>0 && b.step()>b.buttonHeight();
            for(int page=0;page<PlusLayout.PAGE_COUNT;page++){
                int max=PlusLayout.maxScroll(page,b);
                assert PlusLayout.clampScroll(-100,max)==0;
                assert PlusLayout.clampScroll(max+100,max)==max;
                for(int offset:buttonOffsets(page,b)){
                    boolean reachable=false;
                    for(int scroll=0;scroll<=max;scroll++){
                        int top=b.bodyTop()+offset-scroll;
                        if(PlusLayout.fullyVisible(top,b.buttonHeight(),b.bodyTop(),b.bodyBottom())) {reachable=true;break;}
                    }
                    assert reachable : "Control cannot be reached: page="+page+" offset="+offset+" bounds="+b+" max="+max;
                }
            }
        }
        assert LT5PlusScreen.comparisonIdentityMatches("song",3,"sources", "song",3,"sources");
        assert !LT5PlusScreen.comparisonIdentityMatches("song",3,"sources", "next-song",3,"sources");
        assert !LT5PlusScreen.comparisonIdentityMatches("song",3,"sources", "song",4,"sources");
        assert !LT5PlusScreen.comparisonIdentityMatches("song",3,"sources", "song",3,"changed sources");
        assert !LT5PlusScreen.comparisonIdentityMatches("",-1,"", "",-1,"");
        var normal=PlusLayout.forScreen(320,180,2);
        assert normal.scale()==1.0F && normal.bodyHeight()>70;
        String src=Files.readString(Path.of("src/client/java/com/lt5lyrics/client/island/LT5PlusScreen.java"));
        assert src.contains("extends Button") && src.contains("extends EditBox");
        assert src.contains("SettingsPanel.BG_STUDIO") && src.contains("SettingsPanel.BG_CARD");
        assert src.contains("resolveAccentColor") && src.contains("Fonts.medium") && src.contains("Fonts.regular");
        assert src.contains("setResponder") && src.contains("setMaxLength(48)") && src.contains("moveCursorTo");
        assert src.contains("setHighlightPos(int position)") && src.contains("selectionAnchor")
                && !src.contains("value.startsWith(selected,cursor)") : "Highlight must use the real selection endpoint";
        assert src.contains("mouseScrolled") && src.contains("PlusLayout.fullyVisible");
        for(String required:new String[]{"Start test lyrics","Search LRCLIB","Import LRC","TAP when selected line starts",
                "Save current","Apply","Delete","Import style","Export style","Choose pins","Retry lyrics","Compare enabled sources",
                "Undo last settings change","Restore when Studio opened"}) assert src.contains(required) : required;
        assert !src.contains("Button.builder(") : "Extras must not use the old unthemed buttons";
        System.out.println("PASS: Extras Studio palette, styled controls, editable style name, responsive layout and scroll reachability");
    }

    private static int[] buttonOffsets(int page, PlusLayout.Bounds b) {
        int s=b.step(), ten=PlusLayout.unit(b.scale(),10);
        return switch(page){
            case 0 -> new int[]{0,s,2*s};
            case 1 -> new int[]{0,s};
            case 2 -> new int[]{ten,2*ten+s,2*ten+2*s};
            case 3 -> new int[]{s,3*s,5*s,7*s};
            case 4 -> new int[]{0,2*s,3*s};
            case 5 -> new int[]{0,s,2*s,3*s};
            case 6 -> new int[]{4*s};
            case 7,8 -> new int[]{0,s};
            default -> new int[0];
        };
    }
}
