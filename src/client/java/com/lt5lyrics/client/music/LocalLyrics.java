package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocalLyrics {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static Path textFile(String artist, String title) {
		return directory().resolve(safe(artist) + " - " + safe(title) + ".txt");
	}

	public static void editText(String artist, String title) throws IOException {
		Files.createDirectories(directory());
		Path file = textFile(artist, title);
		if(!Files.exists(file)) Files.createFile(file);
		new ProcessBuilder("notepad.exe", file.toAbsolutePath().toString()).start();
	}

	public static List<String> loadText(String artist, String title) throws IOException {
		return Files.readAllLines(textFile(artist, title), StandardCharsets.UTF_8).stream()
			.map(line -> line.replace("\uFEFF", "").trim())
			.filter(line -> !line.isEmpty()).toList();
	}

	public static Path directory() {
		return IslandSettings.configDir().resolve("lyrics");
	}

	public static Path fileFor(String artist, String title) {
		return directory().resolve(safe(artist) + " - " + safe(title) + ".lrc");
	}

	public static List<LyricsFetcher.LyricLine> load(String artist, String title) {
		Path file = fileFor(artist, title);
		if(!Files.isRegularFile(file)) return List.of();
		try{
			String content = Files.readString(file, StandardCharsets.UTF_8);
			return LyricsFetcher.parseLrc(content);
		}catch(IOException e){
			LOGGER.warn("cannot read local lyrics: {}", e.toString());
			return List.of();
		}
	}

	public static boolean save(String artist, String title, List<LyricsFetcher.LyricLine> lines) {
		if(lines.isEmpty()) return false;
		try{
			Files.createDirectories(directory());
			StringBuilder builder = new StringBuilder();
			builder.append("[ar:").append(artist).append("]\n");
			builder.append("[ti:").append(title).append("]\n");
			for(LyricsFetcher.LyricLine line : lines){
				if(line.time() < 0.0F) continue;
				int minutes = (int) (line.time() / 60.0F);
				float seconds = line.time() - minutes * 60.0F;
				builder.append(String.format(Locale.ROOT, "[%02d:%05.2f]%s%n", minutes, seconds, line.text()));
			}
			Path file = fileFor(artist, title);
			Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
			LOGGER.info("saved local lyrics to {}", file);
			return true;
		}catch(IOException e){
			LOGGER.warn("cannot save local lyrics: {}", e.toString());
			return false;
		}
	}

    /** Copy the original UTF-8 LRC verbatim, including enhanced word tags and translations. */
    public static List<LyricsFetcher.LyricLine> importFile(Path source, String artist, String title) throws IOException {
        return importTo(source, fileFor(artist, title));
    }

    public static List<LyricsFetcher.LyricLine> importTo(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Choose an existing .lrc file");
        if (Files.size(source) > 2_097_152L) throw new IOException("LRC file is larger than 2 MB");
        String content = Files.readString(source, StandardCharsets.UTF_8);
        List<LyricsFetcher.LyricLine> lines = LyricsFetcher.parseLrc(content);
        if (lines.isEmpty() || lines.stream().noneMatch(l -> l.time() >= 0 && !l.text().isBlank()))
            throw new IOException("No timed lyric lines were found in that UTF-8 LRC file");
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        if (Files.exists(destination) && Files.isSameFile(source, destination)) return lines;
        if (Files.isRegularFile(destination)) {
            Path backup = destination.resolveSibling(destination.getFileName().toString() + ".before-import");
            for (int n=1; Files.exists(backup); n++) backup = destination.resolveSibling(destination.getFileName() + ".before-import-" + n);
            Files.copy(destination, backup); // Never silently destroy custom lyrics.
        }
        Path temp = destination.resolveSibling(destination.getFileName().toString() + ".importing");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try { Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        return lines;
    }

	public static boolean exists(String artist, String title) {
		return Files.isRegularFile(fileFor(artist, title));
	}

	public static boolean delete(String artist, String title) {
		try{
			boolean removed = Files.deleteIfExists(fileFor(artist, title));
			if(removed) LOGGER.info("removed local lyrics for {} - {}", artist, title);
			return removed;
		}catch(IOException e){
			LOGGER.warn("cannot delete local lyrics: {}", e.toString());
			return false;
		}
	}

	public static List<String> plainLines(List<LyricsFetcher.LyricLine> lines) {
		List<String> texts = new ArrayList<>();
		for(LyricsFetcher.LyricLine line : lines){
			if(!line.text().isBlank()) texts.add(line.text());
		}
		return texts;
	}

	private static String safe(String value) {
		if (value == null) return "";
		return value.replace("\u200B", "").replace("\uFEFF", "").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
	}

	private LocalLyrics() {
	}
}
