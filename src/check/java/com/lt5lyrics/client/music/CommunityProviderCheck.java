package com.lt5lyrics.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.SourceCredits;
import com.lt5lyrics.client.island.StylePresets;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic-only provider, matching, settings, attribution and bounded-transport regression checks. */
public final class CommunityProviderCheck {
    private static final String WORD_XML = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <body dur="01:40.000"><div>
            <p begin="1s" end="4s"><span begin="1s" end="2s">Blue </span><span begin="2s" end="4s">sky</span><span ttm:role="x-translation">Ciel bleu</span></p>
            <p begin="5s" end="8s"><span begin="5s" end="6s">New </span><span begin="6s" end="8s">day</span></p>
            <p begin="9s" end="12s"><span begin="9s" end="10s">Go </span><span begin="10s" end="12s">home</span></p>
          </div></body>
        </tt>
        """;
    private static final String LINE_XML = """
        <tt xmlns="http://www.w3.org/ns/ttml"><body dur="01:40"><div>
        <p begin="1s">Blue sky</p><p begin="5s">New day</p><p begin="9s">Go home</p>
        </div></body></tt>
        """;
    private CommunityProviderCheck() {}
    public static void run() throws Exception {
        metadata(); unison(); amll(); selectionAndCredit(); settings(); transport();
        System.out.println("PASS: Unison/AMLL metadata, formats, duration/version guards, selection, attribution, comparison and bounded HTTP");
    }
    private static void metadata() {
        assert CommunitySourceSupport.metadataMatches("Singer", "Example", "Singer - Example (Official Video)", "Singer", "Example");
        assert !CommunitySourceSupport.metadataMatches("Singer", "Example", "Example", "Someone", "Example");
        assert !CommunitySourceSupport.metadataMatches("Singer", "Example", "Example", "Singer", "Example Part Two");
        assert !CommunitySourceSupport.metadataMatches("Singer", "Example", "Example (Live)", "Singer", "Example");
        assert !CommunitySourceSupport.metadataMatches("Singer", "Example", "Example", "Singer", "Example (Live)");
        assert CommunitySourceSupport.metadataMatches("Singer", "Example", "Example (Live)", "Singer", "Example (Live)");
        assert !CommunitySourceSupport.versionCompatible("Example (Slowed)", "Example (Remix)");
        assert !CommunitySourceSupport.versionCompatible("Example (Radio Edit)", "Example");
        assert CommunitySourceSupport.versionCompatible("Live and Let Die", "Live and Let Die");
        assert CommunitySourceSupport.durationCompatible(100, 108);
        assert !CommunitySourceSupport.durationCompatible(100, 109);
        assert CommunitySourceSupport.durationCompatible(0, 999);
        var doc = BetterLyricsProvider.parseTtmlDocument(WORD_XML);
        assert Math.abs(doc.durationSeconds() - 100F) < .001F && doc.lines().size() == 3;
        assert doc.lines().get(0).translation().equals("Ciel bleu") && LyricsFetcher.hasWordTiming(doc.lines());
        assert !CommunitySourceSupport.timelineFits(30, doc.durationSeconds(), doc.lines());
        assert !CommunitySourceSupport.timelineFits(6, -1, List.of(new LyricsFetcher.LyricLine(25, "Late")));
        assert !CommunitySourceSupport.durationCompatible(0,Float.NaN);
        var invalid=new LyricsFetcher.LyricLine(1,"Word","",List.of(new LyricsFetcher.WordSpan(1,Float.NaN,0,4)));
        assert !CommunitySourceSupport.timelineFits(0,-1,List.of(invalid));
        var impossible=new LyricsFetcher.LyricLine(1,"Word","",List.of(new LyricsFetcher.WordSpan(2,1,0,4)));
        assert !CommunitySourceSupport.timelineFits(100,-1,List.of(impossible));
        String mixed=WORD_XML.replace("<body dur=", "<body xmlns=\"\" dur=");
        assert BetterLyricsProvider.parseTtmlDocument(mixed).lines().size()==3 : "mixed TTML namespaces";
    }
    private static JsonObject unisonEnvelope(String fmt, String content) {
        JsonObject data = new JsonObject();data.addProperty("song", "Example");data.addProperty("artist", "Singer");
        data.addProperty("format", fmt);data.addProperty("syncType", "richsync");data.addProperty("lyrics", content);
        JsonObject root = new JsonObject();root.addProperty("success", true);root.add("data", data);return root;
    }
    private static LyricsFetcher.ProviderHit unisonHit(JsonObject json, long duration) {
        return UnisonLyrics.parseResponse(json.toString(), "Singer", "Example", "Example", duration);
    }
    private static void unison() {
        var word = unisonHit(unisonEnvelope("ttml", WORD_XML), 100);
        assert word.quality() == LyricsFetcher.SyncQuality.WORD_SYNCED && word.synced().size() == 3;
        assert word.source().equals("Unison");
        assert unisonHit(unisonEnvelope("ttml", LINE_XML), 100).quality() == LyricsFetcher.SyncQuality.LINE_SYNCED;
        assert unisonHit(unisonEnvelope("lrc", "[00:01.00]Blue sky\n[00:05.00]New day"), 100).quality() == LyricsFetcher.SyncQuality.LINE_SYNCED : "advertised richsync is not proof";
        assert unisonHit(unisonEnvelope("lrc", "[00:01.00]<00:01.00>Blue <00:02.00>sky\n[00:05.00]New day"), 100).quality() == LyricsFetcher.SyncQuality.WORD_SYNCED;
        assert unisonHit(unisonEnvelope("plain", "Blue sky\nNew day"), 100).quality() == LyricsFetcher.SyncQuality.PLAIN_UNSYNCED;
        assert !unisonHit(unisonEnvelope("plain", "Blue sky\nNew day"), 100).hasSynced();
        assert !unisonHit(unisonEnvelope("unsupported", WORD_XML), 100).hasSynced();
        assert !unisonHit(unisonEnvelope("ttml", WORD_XML), 150).hasSynced();
        var wrong = unisonEnvelope("ttml", WORD_XML);wrong.getAsJsonObject("data").addProperty("artist", "Other");
        assert !unisonHit(wrong,100).hasSynced();
        wrong = unisonEnvelope("ttml", WORD_XML);wrong.getAsJsonObject("data").addProperty("song", "Example (Live)");
        assert !unisonHit(wrong,100).hasSynced();
        wrong = unisonEnvelope("ttml", WORD_XML);wrong.getAsJsonObject("data").addProperty("duration", 20);
        assert !unisonHit(wrong,100).hasSynced();
        assert !UnisonLyrics.parseResponse("<html>blocked</html>", "Singer", "Example", "Example",100).hasSynced();
        assert !unisonHit(unisonEnvelope("ttml", "<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><tt>&x;</tt>"),100).hasSynced();
    }
    private static JsonArray strings(String... values) { JsonArray a = new JsonArray();for (String s : values)a.add(s);return a; }
    private static JsonObject item(long id, String title, String artist) {
        JsonObject o = new JsonObject();o.addProperty("id", id);o.add("musicNames", strings(title));o.add("artistNames", strings(artist));return o;
    }
    private static JsonObject detail(long id, String title, String artist, String xml) {
        JsonObject data=item(id,title,artist);data.addProperty("format", "ttml");data.addProperty("lyrics", xml);
        JsonObject root=new JsonObject();root.addProperty("status",200);root.add("data",data);return root;
    }
    private static void amll() {
        JsonArray items = new JsonArray();items.add(item(102,"Example Part Two","Singer"));items.add(item(101,"Example","Singer"));
        items.add(item(103,"Example (Live)","Singer"));items.add(item(104,"Example","Other"));items.add(item(9_007_199_254_740_992L,"Example","Singer"));
        JsonObject data=new JsonObject();data.add("items",items);JsonObject search=new JsonObject();search.addProperty("status",200);search.add("data",data);
        assert AmllLyrics.parseCandidateIds(search.toString(),"Singer","Example","Example").equals(List.of(101L));
        assert AmllLyrics.parseCandidateIds(search.toString(),"Singer","Example","Example (Live)").equals(List.of(103L));
        var word=AmllLyrics.parseDetailResponse(detail(101,"Example","Singer",WORD_XML).toString(),101,"Singer","Example","Example",100);
        assert word.quality()==LyricsFetcher.SyncQuality.WORD_SYNCED && word.synced().size()==3;
        assert !AmllLyrics.parseDetailResponse(detail(101,"Example","Singer",WORD_XML).toString(),102,"Singer","Example","Example",100).hasSynced();
        assert !AmllLyrics.parseDetailResponse(detail(101,"Example","Singer",WORD_XML).toString(),101,"Singer","Example","Example",120).hasSynced();
        assert !AmllLyrics.parseDetailResponse(detail(101,"Example","Other",WORD_XML).toString(),101,"Singer","Example","Example",100).hasSynced();
        assert AmllLyrics.parseDetailResponse(detail(101,"Example","Singer",LINE_XML).toString(),101,"Singer","Example","Example",100).quality()==LyricsFetcher.SyncQuality.LINE_SYNCED;
    }
    private static void selectionAndCredit() {
        var word=unisonHit(unisonEnvelope("ttml", WORD_XML), 100);
        var line=unisonHit(unisonEnvelope("ttml", LINE_XML), 100);
        var c=LyricsFetcher.comparisonFromHit("unison",word,false);
        assert c.status()==LyricsFetcher.ComparisonStatus.WORD && c.wordLines()==3 && c.totalLines()==3;
        assert LyricsFetcher.comparisonFromHit("unison",line,false).status()==LyricsFetcher.ComparisonStatus.LINE;
        assert LyricsFetcher.comparisonFromHit("unison",LyricsFetcher.ProviderHit.EMPTY,true).status()==LyricsFetcher.ComparisonStatus.UNAVAILABLE;
        assert LyricsFetcher.comparableTimedLyrics(line.synced(), word.synced());
        assert LyricsFetcher.compatibleUpgrade(line,word);
        var previous=new LyricsFetcher.ProviderHit(List.of(new LyricsFetcher.LyricLine(0,"probe")),List.of(),LyricsFetcher.SyncQuality.LINE_SYNCED,"LRCLIB");
        assert LyricsFetcher.compatibleUpgrade(previous,word) : "a one-line placeholder cannot veto an independently verified full hit";
        var unknown=new LyricsFetcher.ProviderHit(word.synced(),List.of(),word.quality(),"Unverified");
        assert !LyricsFetcher.compatibleUpgrade(previous,unknown);
        var priorWithTranslation = new LyricsFetcher.ProviderHit(List.of(
            new LyricsFetcher.LyricLine(1,"Blue sky","Ciel bleu"),new LyricsFetcher.LyricLine(5,"New day"),new LyricsFetcher.LyricLine(9,"Go home")),List.of(),LyricsFetcher.SyncQuality.LINE_SYNCED,"Unison");
        var candidate = new LyricsFetcher.ProviderHit(word.synced().stream().map(l->new LyricsFetcher.LyricLine(l.time(),l.text(),"",l.words())).toList(),List.of(),LyricsFetcher.SyncQuality.WORD_SYNCED,"AMLL TTML DB");
        var merged=LyricsFetcher.preserveTranslations(priorWithTranslation,candidate);
        assert merged.synced().get(0).translation().equals("Ciel bleu") && merged.source().contains("Unison translation");
        assert SourceCredits.needsUnisonCredit("Unison (Word-Sync)",true,true,false);
        assert SourceCredits.needsUnisonCredit(merged.source(),true,true,false);
        assert !SourceCredits.needsUnisonCredit("AMLL TTML DB",true,true,false);
        assert !SourceCredits.needsUnisonCredit("Unison",false,true,false);
        assert !SourceCredits.needsUnisonCredit("Unison",true,true,true);
    }
    private static void settings() throws Exception {
        Path f=Files.createTempFile("lt5-community-settings", ".json");
        try {
            Files.writeString(f,"{\"providerPriority\":[\"kugou\",\"lrclib\"],\"enableLrclib\":false}");
            IslandSettings.enableUnison=false;IslandSettings.enableAmll=false;IslandSettings.loadFromFile(f);
            assert IslandSettings.enableUnison && IslandSettings.enableAmll && !IslandSettings.enableLrclib;
            assert IslandSettings.normalizedProviderPriority().subList(0,2).equals(List.of("kugou","lrclib"));
            IslandSettings.enableUnison=false;IslandSettings.enableAmll=false;IslandSettings.saveToFile(f);
            IslandSettings.enableUnison=true;IslandSettings.enableAmll=true;IslandSettings.loadFromFile(f);
            assert !IslandSettings.enableUnison && !IslandSettings.enableAmll;
            JsonObject style=new JsonObject();style.addProperty("format","lt5lyrics-style-1");style.addProperty("name","Test");
            JsonObject settings=new JsonObject();settings.addProperty("enableUnison",true);settings.addProperty("enableAmll",true);
            style.add("settings",settings);style.add("extras",new JsonObject());
            assert !StylePresets.sanitize(style).getAsJsonObject("settings").has("enableUnison");
            assert !StylePresets.sanitize(style).getAsJsonObject("settings").has("enableAmll");
        } finally {Files.deleteIfExists(f);IslandSettings.resetAllSettingsToFactory();}
    }
    private static void transport() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ok",ex->{byte[] b="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,b.length);try(var out=ex.getResponseBody()){out.write(b);}});
        server.createContext("/rate",ex->{ex.getResponseHeaders().set("Retry-After","7");ex.sendResponseHeaders(429,-1);ex.close();});
        server.createContext("/large",ex->{int size=CommunitySourceSupport.MAX_RESPONSE_BYTES+16384;ex.sendResponseHeaders(200,size);try(var out=ex.getResponseBody()){byte[] chunk=new byte[8192];while(size>0){int n=Math.min(size,chunk.length);out.write(chunk,0,n);size-=n;}}catch(Exception ignored){}});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            assert CommunitySourceSupport.get(base+"/ok").body().equals("{\"ok\":true}");
            var cd=new CommunitySourceSupport.Cooldown();cd.failed(CommunitySourceSupport.get(base+"/not-found"));
            assert !cd.active() : "a normal 404 must not start a cooldown";
            cd.failed(CommunitySourceSupport.get(base+"/rate"));
            assert cd.active();LyricsFetcher.clearCache();assert cd.active() : "manual retry must not bypass 429";
            boolean rejected=false;try {CommunitySourceSupport.get(base+"/large");}catch(Exception expected){rejected=true;}
            assert rejected : "oversized responses must stop during download";
        } finally {server.stop(0);}
    }
}
