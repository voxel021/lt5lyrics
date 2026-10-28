package com.lt5lyrics.client.island;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local, one-step backups for settings. These files may contain personal keys; never export them as styles. */
public final class SettingsHistory {
    private SettingsHistory() {}
    private static Path sidecar(Path file, String suffix) { return file.resolveSibling(file.getFileName().toString() + suffix); }
    private static JsonElement nonNavigation(JsonElement input) {
        JsonElement result = input.deepCopy();
        if (result.isJsonObject()) { JsonObject o = result.getAsJsonObject(); o.remove("lastStudioTab"); o.remove("advancedSettingsMode"); }
        return result;
    }
    static boolean meaningfulChange(String before, String after) {
        try { return !nonNavigation(JsonParser.parseString(before)).equals(nonNavigation(JsonParser.parseString(after))); }
        catch (RuntimeException e) { return false; }
    }
    static void atomicReplace(Path file, String text) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = sidecar(file, ".new"); Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(tmp); }
    }
    public static synchronized void write(Path file, String text) throws IOException {
        if (Files.isRegularFile(file)) {
            String previous = Files.readString(file, StandardCharsets.UTF_8);
            if (previous.equals(text)) return;
            if (meaningfulChange(previous, text)) atomicReplace(sidecar(file, ".undo"), previous);
        }
        atomicReplace(file, text);
    }
    public static synchronized boolean hasUndo(Path a, Path b) { return Files.isRegularFile(sidecar(a, ".undo")) || Files.isRegularFile(sidecar(b, ".undo")); }
    public static synchronized boolean undoLatest(Path a, Path b) throws IOException {
        Path ab=sidecar(a,".undo"), bb=sidecar(b,".undo"); boolean ae=Files.isRegularFile(ab), be=Files.isRegularFile(bb);
        if (!ae && !be) return false; Path original=a, backup=ab;
        if (be && (!ae || Files.getLastModifiedTime(bb).compareTo(Files.getLastModifiedTime(ab))>0)) { original=b; backup=bb; }
        String data=Files.readString(backup,StandardCharsets.UTF_8);
        if(!JsonParser.parseString(data).isJsonObject()) throw new IOException("Backup is not a settings object");
        atomicReplace(original,data); Files.deleteIfExists(backup); return true;
    }
    public static synchronized void checkpoint(Path a, Path b) throws IOException {
        for (Path p : new Path[]{a,b}) { Path snap=sidecar(p,".studio-backup");
            if(Files.isRegularFile(p)) atomicReplace(snap,Files.readString(p,StandardCharsets.UTF_8)); else Files.deleteIfExists(snap); }
    }
    public static synchronized boolean hasCheckpoint(Path a, Path b) { return Files.isRegularFile(sidecar(a,".studio-backup")) || Files.isRegularFile(sidecar(b,".studio-backup")); }
    public static synchronized boolean restoreCheckpoint(Path a, Path b) throws IOException {
        boolean restored=false;
        for(Path p:new Path[]{a,b}) { Path snap=sidecar(p,".studio-backup"); if(!Files.isRegularFile(snap))continue;
            String value=Files.readString(snap,StandardCharsets.UTF_8);
            if(!JsonParser.parseString(value).isJsonObject()) throw new IOException("Invalid Studio backup");
            write(p,value); restored=true; }
        return restored;
    }
}
