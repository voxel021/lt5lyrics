package com.lt5lyrics.client.island;

import com.mojang.blaze3d.platform.InputConstants;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.LocalLyrics;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.SyncTools;
import com.lt5lyrics.client.music.TrackMetadata;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;


/** Everyday optional controls, shared by Simple and Advanced Studio. */
public final class LT5PlusScreen extends Screen {
    private enum Page { WORLD, ISLAND, TIMING, LYRICS, STYLES, PINS, STATUS, HIDE, BACKUP }
    private static final String[] TABS={"World","Island","Timing","Lyrics","Styles","Pins","Status","Hide","Backup"};
    private final MusicTracker tracker; private final Screen parent;
    private Page page=Page.WORLD;
    private int x, top, contentWidth, body, step, bh, footer;
    private PlusLayout.Bounds layout;
    private int bodyScroll, maxBodyScroll;
    private float uiScale = 1.0F;
    private String message=""; private long messageUntil;
    private final LineSelection lineSelection=new LineSelection();
    private List<LyricsFetcher.ManualMatch> matches=List.of(); private String matchesTrack=""; private long matchesEpoch=-1;
    private int matchIndex; private boolean searching;
    private List<LyricsFetcher.SourceComparison> comparisons=List.of(); private int comparisonIndex;
    private boolean comparing; private volatile long comparisonGeneration;
    private String comparedTrack="", comparedSignature=""; private long comparedEpoch=-1;
    private List<String> styleNames=List.of(); private int styleIndex; private String styleDraft="My Style";
    private long confirmDeleteUntil,confirmRestoreUntil; private String confirmDeleteName="";
    private boolean choosingPins; private int pinPage;

    public LT5PlusScreen(MusicTracker tracker,Screen parent){super(Component.literal("LT5Lyrics Extras & Tools"));this.tracker=tracker;this.parent=parent;}
    private void status(String text){message=text==null?"":text;messageUntil=System.currentTimeMillis()+9000L;}
    private void refresh(){clearWidgets();init();}
    private String trackKey(){return MusicTracker.sessionKey(tracker.getSession());}
    private MediaInfo media(){IMediaSession s=tracker.getSession();return s==null?null:s.getMedia();}
    private static String on(boolean b){return b?"ON":"OFF";}
    private static String title(Enum<?> e){String s=e.name().toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private int u(float amount) { return PlusLayout.unit(uiScale, amount); }
    private int colW(int count){return Math.max(1,(contentWidth-(count-1)*layout.gap())/count);}
    private int colX(int i,int count){return x+i*(colW(count)+layout.gap());}

    private enum ButtonKind { TAB, BACK, ACTION }
    private Button themedButton(String label,int bx,int by,int bw,int height,ButtonKind kind,
                                boolean inBody,Runnable action){
        StudioButton button=new StudioButton(label,bx,by,bw,height,kind,inBody,action);
        if(inBody) button.visible=PlusLayout.fullyVisible(by,height,layout.bodyTop(),layout.bodyBottom());
        return addRenderableWidget(button);
    }
    private Button button(String label,int bx,int by,int bw,Runnable action){
        return themedButton(label,bx,by,bw,bh,ButtonKind.ACTION,true,action);
    }
    private void cell(String label,int i,int count,int row,Runnable action){button(label,colX(i,count),body+row*step,colW(count),action);}
    private void plusToggle(Runnable action){action.run();PlusSettings.save();refresh();}
    private void switchPage(int index){page=Page.values()[index];pinPage=0;bodyScroll=0;refresh();}

    @Override protected void init(){
        int guiScale=Minecraft.getInstance().getWindow().getGuiScale();
        layout=PlusLayout.forScreen(width,height,guiScale); uiScale=layout.scale();
        x=layout.contentX();contentWidth=layout.contentWidth();top=layout.panelY();
        step=layout.step();bh=layout.buttonHeight();footer=layout.footerY();
        maxBodyScroll=PlusLayout.maxScroll(page.ordinal(),layout);
        bodyScroll=PlusLayout.clampScroll(bodyScroll,maxBodyScroll);
        body=layout.bodyTop()-bodyScroll;
        themedButton("Back",x+contentWidth-u(45),top+u(5),u(45),u(13),ButtonKind.BACK,false,this::onClose);
        for(int i=0;i<TABS.length;i++){int idx=i,row=i/5,column=i%5;
            Button tab=themedButton(TABS[i],colX(column,5),layout.tabY()+row*layout.tabStep(),
                colW(5),layout.tabHeight(),ButtonKind.TAB,false,()->switchPage(idx));
            tab.active=page.ordinal()!=i;
        }
        switch(page){case WORLD->world();case ISLAND->island();case TIMING->timing();case LYRICS->lyrics();case STYLES->styles();case PINS->pins();case STATUS->statusPage();case HIDE->hide();case BACKUP->backup();}
    }
    private void world(){
        cell("Decorations: "+on(PlusSettings.worldDecorations),0,2,0,()->plusToggle(()->PlusSettings.worldDecorations=!PlusSettings.worldDecorations));
        cell("Weather: "+on(PlusSettings.weatherTheme),1,2,0,()->plusToggle(()->PlusSettings.weatherTheme=!PlusSettings.weatherTheme));
        cell("Distance size: "+on(PlusSettings.distanceSize),0,2,1,()->plusToggle(()->PlusSettings.distanceSize=!PlusSettings.distanceSize));
        cell("Find arrow: "+on(PlusSettings.finderArrow),1,2,1,()->plusToggle(()->PlusSettings.finderArrow=!PlusSettings.finderArrow));
        cell("Start test lyrics",0,2,2,()->{if(tracker.hasSession()){status("Stop/close the music player first; test never replaces a real song.");return;}
            if(!IslandSettings.lyricsEnabled||!IslandSettings.worldText){status("Turn on lyrics and choose World or Sky first.");return;}
            if(TestLyrics.start(tracker))Minecraft.getInstance().gui.setScreen(null);});
        cell("Stop test",1,2,2,()->{TestLyrics.stop();status("Test stopped.");});
    }
    private void island(){
        cell("Progress: "+title(PlusSettings.progressStyle),0,2,0,()->plusToggle(()->PlusSettings.progressStyle=PlusSettings.progressStyle.next()));
        cell("Player badge: "+on(PlusSettings.playerBadge),1,2,0,()->plusToggle(()->PlusSettings.playerBadge=!PlusSettings.playerBadge));
        cell("Song change: "+title(PlusSettings.songAnimation),0,1,1,()->plusToggle(()->PlusSettings.songAnimation=PlusSettings.songAnimation.next()));
    }
    private void updateLine(){lineSelection.update(trackKey(),tracker.trackEpoch(),tracker.getLyrics());}
    private void moveLine(int amount){updateLine();if(lineSelection.isEmpty()){status("No playable lyric lines for this song.");return;}lineSelection.move(amount,tracker.smoothPosition());}
    private void tap(){
        updateLine();if(!tracker.isPlaying()){status("Play the song before tapping.");return;}
        LyricsFetcher.LyricLine selected=lineSelection.selected();if(selected==null){status("First choose a lyric line with Previous or Next.");return;}
        float start=selected.time(),raw=tracker.rawPosition();
        try{float offset=SyncTools.offsetAtTap(start,raw);tracker.setLyricsOffset(offset);
            status(Math.abs(start-raw)>10?"Adjusted to the existing -10 to +10 second limit.":String.format(Locale.ROOT,"Saved per-song timing: %+.1f s",offset));
        }catch(IllegalArgumentException e){status(e.getMessage());}
    }
    private void timing(){
        updateLine();int y=body+u(10);float[] offsets={-1,-.1F,.1F,1};String[] labels={"-1 s","-0.1 s","+0.1 s","+1 s"};
        for(int i=0;i<4;i++){int n=i;button(labels[i],colX(i,4),y,colW(4),()->{if(!tracker.hasSession()){status("Start a song first.");return;}tracker.adjustLyricsOffset(offsets[n]);status("Total timing saved for this song, including any quick adjustment.");});}
        int navY=y+step+u(10);String[] moves={"-5","Previous","Next","+5"};int[] shifts={-5,-1,1,5};
        for(int i=0;i<4;i++){int n=i;button(moves[i],colX(i,4),navY,colW(4),()->moveLine(shifts[n]));}
        button("TAP when selected line starts",x,navY+step,contentWidth,this::tap);
    }
    private void lyrics(){
        cell(searching?"Searching...":"Search LRCLIB",0,3,1,this::search);
        cell("Import LRC",1,3,1,this::importLrc);
        cell("Retry all",2,3,1,()->{tracker.reloadLyrics();matches=List.of();status("Retry requested for the current song.");refresh();});
        cell("Previous",0,3,3,()->{if(!matches.isEmpty())matchIndex=Math.floorMod(matchIndex-1,matches.size());});
        cell("Next",1,3,3,()->{if(!matches.isEmpty())matchIndex=(matchIndex+1)%matches.size();});
        cell("Use result",2,3,3,this::useMatch);
        cell(comparing?"Comparing sources...":"Compare enabled sources",0,1,5,this::compareSources);
        cell("Previous source",0,2,7,()->{if(!comparisons.isEmpty())comparisonIndex=Math.floorMod(comparisonIndex-1,comparisons.size());});
        cell("Next source",1,2,7,()->{if(!comparisons.isEmpty())comparisonIndex=(comparisonIndex+1)%comparisons.size();});
    }
    private String comparisonSignature(){
        StringBuilder b=new StringBuilder();for(String id:IslandSettings.normalizedProviderPriority())b.append(id).append('=').append(IslandSettings.isProviderEnabled(id)).append(';');
        // These remain in memory only; credentials are never shown or sent in the comparison label.
        b.append("betterKey=").append(IslandSettings.betterLyricsApiKey).append(";musixKey=").append(IslandSettings.musixmatchApiKey);
        return b.toString();
    }
    static boolean comparisonIdentityMatches(String expectedTrack,long expectedEpoch,String expectedSignature,
                                             String track,long epoch,String signature){
        return expectedEpoch>=0 && java.util.Objects.equals(expectedTrack,track) && expectedEpoch==epoch
            && java.util.Objects.equals(expectedSignature,signature);
    }
    private boolean comparisonCurrent(long gen,String key,long epoch,String signature){
        return comparisonGeneration==gen && comparisonIdentityMatches(key,epoch,signature,trackKey(),tracker.trackEpoch(),comparisonSignature());
    }
    /** Invalidate completed results too, not just requests that are still running. */
    private boolean discardStaleComparison(){
        if(comparedEpoch<0 || comparisonCurrent(comparisonGeneration,comparedTrack,comparedEpoch,comparedSignature))return false;
        comparisonGeneration++; comparedEpoch=-1; comparedTrack=""; comparedSignature="";
        comparing=false; comparisons=List.of(); comparisonIndex=0;
        status("Song or sources changed; compare again.");return true;
    }
    private void compareSources(){
        if(comparing)return;IMediaSession session=tracker.getSession();MediaInfo m=media();
        if(session==null||m==null){status("Start a song first.");return;}
        if(tracker.isFetching()){status("Wait for the automatic lyric search to finish, then compare.");return;}
        String key=trackKey(),signature=comparisonSignature(),owner=session.getOwner()==null?"":session.getOwner();
        String artist=m.getArtist(),song=m.getTitle();long duration=m.getDuration(),epoch=tracker.trackEpoch(),gen=++comparisonGeneration;
        comparedTrack=key;comparedSignature=signature;comparedEpoch=epoch;
        comparisons=List.of();comparisonIndex=0;comparing=true;status("Checking enabled sources; the current lyrics will not change.");refresh();
        CompletableFuture.runAsync(()->LyricsFetcher.compareSources(owner,artist,song,duration,
            ()->comparisonCurrent(gen,key,epoch,signature),entry->Minecraft.getInstance().execute(()->{
                if(!comparisonCurrent(gen,key,epoch,signature))return;
                List<LyricsFetcher.SourceComparison> next=new ArrayList<>(comparisons);next.add(entry);comparisons=List.copyOf(next);
                if(Minecraft.getInstance().gui.screen()==this && page==Page.LYRICS)refresh();
            }))).whenComplete((unused,error)->Minecraft.getInstance().execute(()->{
                if(comparisonGeneration!=gen)return;
                comparing=false;
                if(!comparisonCurrent(gen,key,epoch,signature)){comparisons=List.of();status("Song or sources changed; compare again.");}
                else if(error!=null)status("Comparison stopped; try again later.");
                else status("Comparison complete. Timing data is not a test against audio.");
                if(Minecraft.getInstance().gui.screen()==this && page==Page.LYRICS)refresh();
            }));
    }
    private void search(){
        if(searching)return;IMediaSession s=tracker.getSession();MediaInfo m=media();
        if(s==null||m==null){status("Start a song first.");return;}
        if(!IslandSettings.enableLrclib){status("Enable LRCLIB in Advanced > Music & Lyrics Sources first.");return;}
        String key=trackKey();long epoch=tracker.trackEpoch();TrackMetadata.QueryPair q=TrackMetadata.displayPair(m.getArtist(),m.getTitle());
        long duration=m.getDuration();searching=true;matches=List.of();status("Searching LRCLIB for this song...");refresh();
        CompletableFuture.supplyAsync(()->{try{return LyricsFetcher.searchManualLrclib(q.artist(),q.title(),duration);}
            catch(Exception e){throw new java.util.concurrent.CompletionException(e);}}).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            searching=false;
            if(!key.equals(trackKey())||epoch!=tracker.trackEpoch()||!IslandSettings.enableLrclib){matches=List.of();status("Song or source changed; search again.");}
            else if(error!=null){Throwable cause=error.getCause()==null?error:error.getCause();matches=List.of();status("Search failed: "+cause.getMessage());}
            else{matches=result;matchIndex=0;matchesTrack=key;matchesEpoch=epoch;status(matches.isEmpty()?"No results. Try Retry all or import an LRC.":"Choose a result and press Use result.");}
            if(Minecraft.getInstance().gui.screen()==this)refresh();
        }));
    }
    private void useMatch(){
        if(matches.isEmpty()){status("Search LRCLIB first.");return;}
        if(!IslandSettings.enableLrclib||!matchesTrack.equals(trackKey())||matchesEpoch!=tracker.trackEpoch()){matches=List.of();status("Song or source changed; search again.");return;}
        if(tracker.useManualLyrics(matchesEpoch,matchesTrack,matches.get(matchIndex).result())){matches=List.of();status("Selected for this play. Retry all restores automatic matching.");}
        else status("Song changed; search again.");
    }
    private void importLrc(){
        MediaInfo m=media();if(m==null){status("Start the song the LRC belongs to first.");return;}
        String key=trackKey(),artist=m.getArtist()==null?"":m.getArtist(),title=m.getTitle()==null?"":m.getTitle();long epoch=tracker.trackEpoch();
        DesktopFiles.choose(false,"Choose a timed LRC lyric file","lrc",null,(file,error)->{
            if(error!=null){status(error);return;}if(file==null)return;
            if(!key.equals(trackKey())||epoch!=tracker.trackEpoch()){status("Song changed while choosing; import again.");return;}
            try{List<LyricsFetcher.LyricLine> lines=LocalLyrics.importFile(file,artist,title);boolean words=LyricsFetcher.hasWordTiming(lines);
                LyricsFetcher.Result result=new LyricsFetcher.Result(lines,lines,true,true,"Local (.lrc)",words?LyricsFetcher.SyncQuality.WORD_SYNCED:LyricsFetcher.SyncQuality.LINE_SYNCED);
                boolean applied=tracker.useManualLyrics(epoch,key,result);
                status(applied?"LRC imported for this song; any old local file was backed up.":"LRC saved for the earlier song, but the current song changed.");
            }catch(Exception e){status("Import failed: "+e.getMessage());}
        });
    }
    private void loadStyleNames(){try{styleNames=StylePresets.names();styleIndex=styleNames.isEmpty()?0:Math.clamp(styleIndex,0,styleNames.size()-1);}catch(Exception e){styleNames=List.of();status("Styles could not be read: "+e.getMessage());}}
    private String currentStyle(){return styleNames.isEmpty()?null:styleNames.get(Math.clamp(styleIndex,0,styleNames.size()-1));}
    private void styles(){
        loadStyleNames();StudioEditBox name=new StudioEditBox(x,body,Math.max(u(30),contentWidth-colW(3)-layout.gap()),bh);
        name.visible=PlusLayout.fullyVisible(body,bh,layout.bodyTop(),layout.bodyBottom());
        addRenderableWidget(name);
        name.setMaxLength(48);name.setValue(styleDraft);name.setResponder(v->styleDraft=v);
        button("Save current",x+contentWidth-colW(3),body,colW(3),()->{try{String n=StylePresets.saveNamed(styleDraft);loadStyleNames();styleIndex=Math.max(0,styleNames.indexOf(n));status("Saved style: "+n);refresh();}catch(Exception e){status("Could not save style: "+e.getMessage());}});
        cell("Previous",0,4,2,()->{if(!styleNames.isEmpty())styleIndex=Math.floorMod(styleIndex-1,styleNames.size());});
        cell("Next",1,4,2,()->{if(!styleNames.isEmpty())styleIndex=(styleIndex+1)%styleNames.size();});
        cell("Apply",2,4,2,()->{try{String n=currentStyle();if(n==null){status("Save or import a style first.");return;}StylePresets.apply(StylePresets.get(n));status("Applied "+n+" (appearance/layout only).");}catch(Exception e){status("Could not apply style: "+e.getMessage());}});
        cell("Delete",3,4,2,()->{String n=currentStyle();if(n==null){status("No style selected.");return;}
            if(System.currentTimeMillis()>confirmDeleteUntil||!n.equals(confirmDeleteName)){confirmDeleteUntil=System.currentTimeMillis()+5000;confirmDeleteName=n;status("Press Delete again within 5 seconds to remove "+n+".");return;}
            try{StylePresets.delete(n);confirmDeleteUntil=0;confirmDeleteName="";loadStyleNames();status("Deleted style "+n);refresh();}catch(Exception e){status("Could not delete style: "+e.getMessage());}});
        cell("Import style",0,2,3,()->DesktopFiles.choose(false,"Import LT5Lyrics style","json",null,(path,error)->{if(error!=null){status(error);return;}if(path==null)return;
            try{String n=StylePresets.importFile(path);loadStyleNames();styleIndex=Math.max(0,styleNames.indexOf(n));status("Imported "+n+". Apply it when ready.");if(Minecraft.getInstance().gui.screen()==this)refresh();}catch(Exception e){status("Import failed: "+e.getMessage());}}));
        cell("Export style",1,2,3,()->{String n=currentStyle();if(n==null){status("Save or select a style first.");return;}
            String safe=n.replaceAll("[\\\\/:*?\"<>|]","_");DesktopFiles.choose(true,"Export LT5Lyrics style","json",safe+".json",(path,error)->{if(error!=null){status(error);return;}if(path==null)return;
                try{StylePresets.exportFile(n,path);status("Exported appearance only; no API keys.");}catch(Exception e){status("Export failed: "+e.getMessage());}});});
    }
    private record Pin(String id,String label,BooleanSupplier value,Runnable toggle){}
    private List<Pin> allPins(){return List.of(
        new Pin("LYRICS","Lyrics",()->IslandSettings.lyricsEnabled,()->IslandSettings.lyricsEnabled=!IslandSettings.lyricsEnabled),
        new Pin("TRANSLATIONS","Translations",()->IslandSettings.showTranslations,()->IslandSettings.showTranslations=!IslandSettings.showTranslations),
        new Pin("GLOW","Glow",()->IslandSettings.lyricGlowEnabled,()->IslandSettings.lyricGlowEnabled=!IslandSettings.lyricGlowEnabled),
        new Pin("BACKGROUND","World background",()->IslandSettings.hologramBackground,()->IslandSettings.hologramBackground=!IslandSettings.hologramBackground),
        new Pin("HUD","Music bar",()->IslandSettings.showHud,()->IslandSettings.showHud=!IslandSettings.showHud),
        new Pin("DECOR","Decorations",()->PlusSettings.worldDecorations,()->PlusSettings.worldDecorations=!PlusSettings.worldDecorations),
        new Pin("WEATHER","Weather",()->PlusSettings.weatherTheme,()->PlusSettings.weatherTheme=!PlusSettings.weatherTheme),
        new Pin("DISTANCE","Distance size",()->PlusSettings.distanceSize,()->PlusSettings.distanceSize=!PlusSettings.distanceSize),
        new Pin("ARROW","Find arrow",()->PlusSettings.finderArrow,()->PlusSettings.finderArrow=!PlusSettings.finderArrow),
        new Pin("BADGE","Player badge",()->PlusSettings.playerBadge,()->PlusSettings.playerBadge=!PlusSettings.playerBadge),
        new Pin("HIDE_INV","Hide in inventory",()->PlusSettings.hideInventory,()->PlusSettings.hideInventory=!PlusSettings.hideInventory),
        new Pin("HIDE_CHAT","Hide in chat",()->PlusSettings.hideChat,()->PlusSettings.hideChat=!PlusSettings.hideChat));}
    private List<Pin> visiblePins(){List<Pin> all=allPins();if(choosingPins)return all;List<Pin> out=new ArrayList<>();for(Pin p:all)if(PlusSettings.favorites.contains(p.id()))out.add(p);return out;}
    private void pins(){
        cell(choosingPins?"Show my pins":"Choose pins",0,2,0,()->{choosingPins=!choosingPins;pinPage=0;refresh();});
        cell("Page "+(pinPage+1),1,2,0,()->{int pages=Math.max(1,(visiblePins().size()+5)/6);pinPage=(pinPage+1)%pages;refresh();});
        List<Pin> list=visiblePins();int from=pinPage*6;
        for(int i=0;i<6&&from+i<list.size();i++){Pin p=list.get(from+i);int idx=i;
            String text=choosingPins?(PlusSettings.favorites.contains(p.id())?"[x] ":"[ ] ")+p.label():p.label()+": "+on(p.value().getAsBoolean());
            cell(text,idx%2,2,1+idx/2,()->{if(choosingPins){if(!PlusSettings.favorites.add(p.id()))PlusSettings.favorites.remove(p.id());PlusSettings.save();}
                else{p.toggle().run();IslandSettings.save();PlusSettings.save();}refresh();});}
    }
    private void statusPage(){
        cell("Retry lyrics",0,2,4,()->{tracker.reloadLyrics();status("Retry requested.");});
        cell("Timing tools",1,2,4,()->{page=Page.TIMING;bodyScroll=0;refresh();});
    }
    private void hide(){
        cell("Inventories: "+on(PlusSettings.hideInventory),0,2,0,()->plusToggle(()->PlusSettings.hideInventory=!PlusSettings.hideInventory));
        cell("Chat: "+on(PlusSettings.hideChat),1,2,0,()->plusToggle(()->PlusSettings.hideChat=!PlusSettings.hideChat));
        cell("Other screens: "+on(PlusSettings.hideOtherScreens),0,1,1,()->plusToggle(()->PlusSettings.hideOtherScreens=!PlusSettings.hideOtherScreens));
    }
    private void reloadSettings(){IslandSettings.load();PlusSettings.load();WorldLyrics.onSkySettingsChanged();}
    private void backup(){
        cell("Undo last settings change",0,1,0,()->{try{if(SettingsHistory.undoLatest(IslandSettings.configFile(),PlusSettings.configFile())){reloadSettings();status("Undid the last saved settings change.");}else status("No settings change to undo.");refresh();}catch(Exception e){status("Undo failed: "+e.getMessage());}});
        cell("Restore when Studio opened",0,1,1,()->{if(System.currentTimeMillis()>confirmRestoreUntil){confirmRestoreUntil=System.currentTimeMillis()+5000;status("Press again within 5 seconds to restore the Studio-opening backup.");return;}
            try{boolean ok=SettingsHistory.restoreCheckpoint(IslandSettings.configFile(),PlusSettings.configFile());confirmRestoreUntil=0;if(ok){reloadSettings();status("Restored the Studio-opening settings.");}else status("No Studio-opening backup found.");refresh();}catch(Exception e){status("Restore failed: "+e.getMessage());}});
    }
    private int accent(){return IslandSettings.resolveAccentColor(tracker.getMediaColor());}
    private static int withAlpha(int color,int a){return (Math.clamp(a,0,255)<<24)|(color&0x00FFFFFF);}
    private static void rounded(float x,float y,float w,float h,float r,int color){
        IslandRender.drawRoundedRect(x,y,w,h,r,r,r,r,color);
    }
    private com.lt5lyrics.client.island.font.Font medium(float size){return Fonts.medium(size*uiScale);}
    private com.lt5lyrics.client.island.font.Font regular(float size){return Fonts.regular(size*uiScale);}
    private void clipBody(){IslandRender.pushScissor(x,layout.bodyTop(),contentWidth,layout.bodyHeight());}
    private void label(com.lt5lyrics.client.island.font.Font f,String text,float xx,float yy,float max,int color){
        if(text==null||text.isEmpty()||max<=0)return;
        IslandRender.drawWindowedText(f,text,xx,yy,color,xx,xx+max,u(5),0);
    }
    private void text(GuiGraphicsExtractor g,String text,int xx,int yy,int max,int color){
        label(regular(5.4F),text,xx,yy,max,color);
    }

    /** Uses the same fonts, dark cards, accent and rounded controls as the Studio. */
    private final class StudioButton extends Button {
        private final ButtonKind kind; private final boolean inBody;
        StudioButton(String label,int bx,int by,int bw,int height,ButtonKind kind,boolean inBody,Runnable action){
            super(bx,by,bw,height,Component.literal(label),b->action.run(),DEFAULT_NARRATION);
            this.kind=kind;this.inBody=inBody;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
            if(inBody)clipBody();
            try {
                float bx=getX(),by=getY(),bw=getWidth(),h=getHeight(),r=u(4);
                boolean hover=isHoveredOrFocused();int accent=accent();String value=getMessage().getString();
                if(isFocused()) rounded(bx-u(0.7F),by-u(0.7F),bw+u(1.4F),h+u(1.4F),r+u(1),withAlpha(accent,180));
                if(kind==ButtonKind.TAB){
                    boolean selected=!active;
                    rounded(bx,by,bw,h,r,selected?withAlpha(accent,230):withAlpha(0xFF323B49,hover?245:215));
                    com.lt5lyrics.client.island.font.Font font=medium(5.3F);
                    float tx=bx+Math.max(u(2),(bw-font.width(value))/2.0F);
                    label(font,value,tx,by+(h-u(5.3F))/2.0F,Math.max(0,bx+bw-u(2)-tx),selected?0xFF0B1016:SettingsPanel.TEXT_MAIN);
                    return;
                }
                if(kind==ButtonKind.BACK){
                    rounded(bx,by,bw,h,r,withAlpha(0xFFFFFFFF,hover?65:32));
                    com.lt5lyrics.client.island.font.Font font=medium(5.4F);
                    float tx=bx+Math.max(u(2),(bw-font.width(value))/2.0F);
                    label(font,value,tx,by+(h-u(5.4F))/2.0F,bx+bw-u(2)-tx,SettingsPanel.TEXT_MAIN);
                    return;
                }
                boolean pin=value.startsWith("[x] ")||value.startsWith("[ ] ");
                boolean binary=value.endsWith(": ON")||value.endsWith(": OFF")||pin;
                boolean enabled=value.endsWith(": ON")||value.startsWith("[x] ");
                if(binary){
                    String name=pin?value.substring(4):value.substring(0,value.lastIndexOf(": "));
                    rounded(bx,by,bw,h,r,withAlpha(SettingsPanel.BG_CARD,hover?245:225));
                    float sw=Math.min(u(28),Math.max(u(18),bw*0.24F)), sh=Math.min(u(11),h-u(3));
                    float sx=bx+bw-sw-u(5), sy=by+(h-sh)/2.0F;
                    label(medium(5.5F),name,bx+u(6),by+(h-u(5.5F))/2.0F,Math.max(1,sx-bx-u(9)),SettingsPanel.TEXT_MAIN);
                    rounded(sx,sy,sw,sh,sh/2,enabled?withAlpha(accent,230):withAlpha(0xFF343C4A,235));
                    float knob=sh-u(2),kx=enabled?sx+sw-knob-u(1):sx+u(1);
                    rounded(kx,sy+u(1),knob,knob,knob/2,0xFFF4F6FB);
                    return;
                }
                int separator=value.indexOf(": ");
                if(separator>0){
                    String name=value.substring(0,separator),setting=value.substring(separator+2);
                    rounded(bx,by,bw,h,r,withAlpha(SettingsPanel.BG_CARD,hover?245:225));
                    com.lt5lyrics.client.island.font.Font font=medium(5.4F);
                    float badge=Math.min(Math.max(u(28),font.width(setting)+u(10)),bw*0.42F);
                    float sx=bx+bw-badge-u(4);
                    label(font,name,bx+u(6),by+(h-u(5.4F))/2.0F,Math.max(1,sx-bx-u(8)),SettingsPanel.TEXT_MAIN);
                    rounded(sx,by+u(2),badge,h-u(4),u(4),withAlpha(accent,52));
                    float tx=sx+Math.max(u(2),(badge-font.width(setting))/2.0F);
                    label(font,setting,tx,by+(h-u(5.4F))/2.0F,sx+badge-u(2)-tx,withAlpha(accent,255));
                    return;
                }
                boolean primary=value.startsWith("TAP ")||value.equals("Use result")||value.equals("Save current")||value.equals("Start test lyrics");
                rounded(bx,by,bw,h,r,primary?withAlpha(accent,hover?210:165):withAlpha(0xFF303A49,hover?245:210));
                com.lt5lyrics.client.island.font.Font font=medium(5.5F);
                float tx=bx+Math.max(u(3),(bw-font.width(value))/2.0F);
                label(font,value,tx,by+(h-u(5.5F))/2.0F,Math.max(1,bx+bw-u(3)-tx),primary?0xFF101722:SettingsPanel.TEXT_MAIN);
            } finally { if(inBody)IslandRender.popScissor(); }
        }
    }

    /** Native EditBox handles keys, clipboard, narration and selection; only its appearance changes. */
    private final class StudioEditBox extends EditBox {
        private int selectionAnchor;
        StudioEditBox(int bx,int by,int bw,int h){super(LT5PlusScreen.this.font,bx,by,bw,h,Component.literal("Style name"));}
        @Override public void setHighlightPos(int position){
            super.setHighlightPos(position);
            // EditBox keeps this endpoint private. Mirror it instead of guessing
            // from the selected text, which is ambiguous for repeated letters.
            selectionAnchor=Math.clamp(position,0,getValue().length());
        }
        private float textLeft(){return getX()+u(6);}
        private float innerWidth(){return Math.max(1,getWidth()-u(12));}
        private int viewStart(){
            String value=getValue();int cursor=Math.clamp(getCursorPosition(),0,value.length());
            var font=regular(6.0F);int start=0;
            while(start<cursor&&font.width(value.substring(start,cursor))>innerWidth()-u(2)){
                start=value.offsetByCodePoints(start,1);
            }
            return start;
        }
        private int indexAt(double mouseX){
            String value=getValue();int start=viewStart();var font=regular(6.0F);
            float target=(float)mouseX-textLeft();if(target<=0)return start;
            int index=start;float previous=0;
            while(index<value.length()){
                int next=value.offsetByCodePoints(index,1);float end=font.width(value.substring(start,next));
                if(target<(previous+end)*0.5F)return index;
                previous=end;index=next;
            }
            return value.length();
        }
        @Override public void onClick(MouseButtonEvent event,boolean doubleClick){
            int at=indexAt(event.x());
            if(!doubleClick){moveCursorTo(at,event.hasShiftDown());return;}
            String value=getValue();int from=at,to=at;
            while(from>0&&!Character.isWhitespace(value.codePointBefore(from)))from=value.offsetByCodePoints(from,-1);
            while(to<value.length()&&!Character.isWhitespace(value.codePointAt(to)))to=value.offsetByCodePoints(to,1);
            moveCursorTo(from,false);moveCursorTo(to,true);
        }
        @Override protected void onDrag(MouseButtonEvent event,double dx,double dy){moveCursorTo(indexAt(event.x()),true);}
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
            clipBody();
            try{
                float bx=getX(),by=getY(),bw=getWidth(),h=getHeight();int accent=accent();
                rounded(bx,by,bw,h,u(4),isFocused()?withAlpha(accent,170):withAlpha(0xFF536071,150));
                rounded(bx+u(1),by+u(1),bw-u(2),h-u(2),u(3),withAlpha(SettingsPanel.BG_PREVIEW,245));
                String value=getValue();int start=viewStart(),cursor=Math.clamp(getCursorPosition(),0,value.length());
                var font=regular(6.0F);float left=textLeft(),base=by+(h-u(6))/2.0F;
                IslandRender.pushScissor(left,by+u(1),innerWidth(),h-u(2));
                try{
                    int anchor=Math.clamp(selectionAnchor,0,value.length());
                    int a=Math.max(start,Math.min(cursor,anchor)),b=Math.max(start,Math.max(cursor,anchor));
                    if(a<b){
                        float x1=left+font.width(value.substring(start,a)),x2=left+font.width(value.substring(start,b));
                        rounded(x1,by+u(2),Math.max(1,x2-x1),h-u(4),u(1),withAlpha(accent,95));
                    }
                    label(font,value.substring(start),left,base,innerWidth(),SettingsPanel.TEXT_MAIN);
                    if(isFocused()&&((System.currentTimeMillis()/500L)%2L==0L)){
                        float caret=left+font.width(value.substring(start,cursor));
                        rounded(caret,by+u(3),Math.max(1,u(0.7F)),h-u(6),u(0.5F),0xFFFFFFFF);
                    }
                }finally{IslandRender.popScissor();}
            }finally{IslandRender.popScissor();}
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        boolean refreshNeeded=discardStaleComparison();
        if(!matches.isEmpty() && (!matchesTrack.equals(trackKey()) || matchesEpoch!=tracker.trackEpoch() || !IslandSettings.enableLrclib)){
            matches=List.of();matchIndex=0;status("Song or source changed; search again.");refreshNeeded=true;
        }
        if(refreshNeeded)refresh();
        g.fill(0,0,width,height,0x7006080E);
        float px=layout.panelX(),py=layout.panelY(),pw=layout.panelWidth(),ph=layout.panelHeight(),r=u(10);int accent=accent();
        if(IslandSettings.hudOpacity>0.35F)IslandRender.drawBlur(px,py,pw,ph,18.0F,2.0F,r,r,r,r,0xFFFFFFFF);
        rounded(px-u(0.8F),py-u(0.8F),pw+u(1.6F),ph+u(1.6F),r+u(1),withAlpha(SettingsPanel.STROKE_SUBTLE,65));
        rounded(px,py,pw,ph,r,withAlpha(SettingsPanel.BG_STUDIO,245));
        var titleFont=medium(7.2F);String title="LT5Lyrics Studio";
        label(titleFont,title,x,top+u(6),contentWidth-u(50),SettingsPanel.TEXT_MAIN);
        float badgeX=x+titleFont.width(title)+u(5),badgeW=u(25);
        if(badgeX+badgeW<x+contentWidth-u(50)){
            rounded(badgeX,top+u(6),badgeW,u(9),u(4),withAlpha(accent,50));
            var badge=medium(5.1F);String ver="v"+com.lt5lyrics.client.VersionCheck.VERSION;
            label(badge,ver,badgeX+(badgeW-badge.width(ver))/2,top+u(8),badgeW-u(2),withAlpha(accent,245));
        }
        label(regular(5.4F),"Extras & Tools · your choices apply across Simple and Advanced.",x,top+u(21),contentWidth,SettingsPanel.TEXT_SUB);
        super.extractRenderState(g,mouseX,mouseY,delta);
        label(medium(5.6F),TABS[page.ordinal()],x+u(2),layout.sectionY(),contentWidth-u(4),withAlpha(accent,255));
        clipBody();
        try{
            switch(page){
                case WORLD->text(g,"Optional effects; fixed anchors never move.",x,body+3*step+u(2),contentWidth,SettingsPanel.TEXT_SUB);
                case ISLAND->{text(g,"A browser badge names the browser, not a website.",x,body+2*step+u(2),contentWidth,SettingsPanel.TEXT_SUB);text(g,"Slide uses the existing song-change showcase.",x,body+2*step+u(12),contentWidth,SettingsPanel.TEXT_SUB);}
                case TIMING->{updateLine();text(g,String.format(Locale.ROOT,"Total timing: %+.1f s  (+ earlier); buttons save it",tracker.getLyricsOffset()),x,body,contentWidth,SettingsPanel.TEXT_MAIN);
                    LyricsFetcher.LyricLine chosen=lineSelection.selected();String line=chosen==null?(lineSelection.isEmpty()?"No playable lyric lines":"Choose a line with Previous or Next"):(lineSelection.index()+1)+"/"+lineSelection.size()+"  "+MusicTracker.formatTime((long)chosen.time())+"  "+chosen.text();
                    text(g,line,x,body+u(10)+step+u(1),contentWidth,withAlpha(accent,245));}
                case LYRICS->{MediaInfo m=media();text(g,m==null?"No active song":String.valueOf(m.getArtist())+" - "+String.valueOf(m.getTitle()),x,body+u(2),contentWidth,SettingsPanel.TEXT_MAIN);
                    String result="No results selected. Only enabled LRCLIB is searched.";if(!matches.isEmpty()){var a=matches.get(Math.clamp(matchIndex,0,matches.size()-1));result=(matchIndex+1)+"/"+matches.size()+" ["+a.result().syncQuality().badge+" "+MusicTracker.formatTime(a.duration())+"] "+a.artist()+" - "+a.title()+" / "+a.album();}
                    text(g,result,x,body+2*step+u(3),contentWidth,withAlpha(accent,245));text(g,"LRC import keeps timestamps and word tags; old files are backed up.",x,body+4*step+u(1),contentWidth,SettingsPanel.TEXT_SUB);
                    String comparison=comparing?"Checking sources...":"Compare for this song; current selection stays unchanged.";
                    if(!comparisons.isEmpty()){var c=comparisons.get(Math.clamp(comparisonIndex,0,comparisons.size()-1));comparison=(comparisonIndex+1)+"/"+comparisons.size()+"  "+c.name()+": "+c.description();}
                    text(g,comparison,x,body+6*step+u(3),contentWidth,withAlpha(accent,245));
                    text(g,"Checks timestamps, not audio. Editions and alignment may differ.",x,body+8*step+u(1),contentWidth,SettingsPanel.TEXT_SUB);}
                case STYLES->{String n=currentStyle();text(g,n==null?"No saved styles":"Selected: "+n+" ("+(styleIndex+1)+"/"+styleNames.size()+")",x,body+step+u(3),contentWidth,withAlpha(accent,245));text(g,"Styles never include providers, timing, or API keys.",x,body+4*step+u(1),contentWidth,SettingsPanel.TEXT_SUB);}
                case PINS->{if(visiblePins().isEmpty())text(g,"Choose pins to add quick-access controls.",x,body+step+u(2),contentWidth,SettingsPanel.TEXT_SUB);}
                case STATUS->{MediaInfo m=media();String player=m==null?"no active song":PlayerBadge.detect(tracker.getSession().getOwner());text(g,"Player: "+(player==null?"unidentified":player),x,body,contentWidth,SettingsPanel.TEXT_MAIN);
                    text(g,tracker.getStatusHeadline(),x,body+u(10),contentWidth,withAlpha(accent,245));
                    String vis=!IslandSettings.lyricsEnabled?"Lyrics are disabled.":!IslandSettings.worldText?"Top Bar only; World/Sky lyrics are off.":IslandSettings.skyLyrics?"Sky lyrics selected.":"World lyrics selected.";
                    text(g,vis,x,body+u(20),contentWidth,SettingsPanel.TEXT_MAIN);text(g,"Possible issues: wrong direction, walls, F1, or screen-hide rules.",x,body+u(30),contentWidth,SettingsPanel.TEXT_SUB);text(g,"Check source, playback, size and timing if lyrics are missing.",x,body+u(40),contentWidth,SettingsPanel.TEXT_SUB);}
                case HIDE->text(g,"The Studio stays visible for recovery.",x,body+2*step+u(2),contentWidth,SettingsPanel.TEXT_SUB);
                case BACKUP->{text(g,"Backups may contain API keys; do not share them.",x,body+2*step+u(2),contentWidth,SettingsPanel.TEXT_SUB);text(g,"Local LRCs, styles and per-song delays are not included.",x,body+2*step+u(12),contentWidth,SettingsPanel.TEXT_SUB);}
            }
        }finally{IslandRender.popScissor();}
        if(maxBodyScroll>0){
            float trackX=px+pw-u(5), trackY=layout.bodyTop(),trackH=layout.bodyHeight();
            rounded(trackX,trackY,u(2),trackH,u(1),withAlpha(0xFFFFFFFF,35));
            float thumbH=Math.max(u(10),trackH*trackH/(trackH+maxBodyScroll));
            float thumbY=trackY+(trackH-thumbH)*bodyScroll/maxBodyScroll;
            rounded(trackX,thumbY,u(2),thumbH,u(1),withAlpha(accent,185));
        }
        rounded(px+u(4),footer,pw-u(8),u(12),u(4),withAlpha(0xFF080B11,220));
        String note=System.currentTimeMillis()<messageUntil?message:(maxBodyScroll>0?"Scroll for more · Changes save automatically.":"Changes save automatically · Back returns to Studio.");
        label(regular(5.0F),note,x,footer+u(4),contentWidth,System.currentTimeMillis()<messageUntil?0xFFFFD77A:SettingsPanel.TEXT_DIM);
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){
        if(layout!=null&&maxBodyScroll>0&&mouseX>=layout.panelX()&&mouseX<=layout.panelX()+layout.panelWidth()
                &&mouseY>=layout.bodyTop()&&mouseY<=layout.bodyBottom()){
            int delta=(int)Math.round(-scrollY*step);
            if(delta==0&&scrollY!=0)delta=scrollY<0?step:-step;
            bodyScroll=PlusLayout.clampScroll(bodyScroll+delta,maxBodyScroll);refresh();return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
    @Override public boolean keyPressed(KeyEvent event){if(page==Page.TIMING&&event.key()==InputConstants.KEY_SPACE){tap();return true;}return super.keyPressed(event);}
    @Override public void onClose(){comparisonGeneration++;comparing=false;IslandSettings.save();PlusSettings.save();Minecraft.getInstance().gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
