package com.lt5lyrics.client.island;
import com.lt5lyrics.client.music.LyricsFetcher;
import java.util.List;
/** An explicitly chosen lyric line is valid only for the same song and lyric revision. */
final class LineSelection {
    private String track=""; private long epoch=-1; private List<LyricsFetcher.LyricLine> lines=List.of(); private int index=-1;
    void update(String newTrack,long newEpoch,List<LyricsFetcher.LyricLine> newLines) {
        if(!track.equals(newTrack)||epoch!=newEpoch||!lines.equals(newLines)) {
            track=newTrack;epoch=newEpoch;lines=List.copyOf(newLines);index=-1;
        }
    }
    void move(int amount,float playbackPosition) {
        if(lines.isEmpty()||amount==0)return; int base=index;
        if(base<0){base=Math.max(0,LyricsFetcher.currentIndex(lines,playbackPosition));amount+=amount>0?-1:1;}
        index=Math.clamp(base+amount,0,lines.size()-1);
    }
    boolean isEmpty(){return lines.isEmpty();} int index(){return index;} int size(){return lines.size();}
    LyricsFetcher.LyricLine selected(){return index>=0&&index<lines.size()?lines.get(index):null;}
}
