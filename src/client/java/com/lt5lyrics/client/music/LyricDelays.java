package com.lt5lyrics.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LyricDelays {

	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");
	private static final Map<String, Float> DELAYS = new HashMap<>();
	private static boolean loaded;

	private static Path file() {
		return IslandSettings.configDir().resolve("delays.json");
	}

	private static String key(String artist, String title) {
		return artist.trim() + " - " + title.trim();
	}

	private static void load() {
		loaded = true;
		Path path = file();
		if(!Files.isRegularFile(path)) return;
		try{
			JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			for(String name : json.keySet()) DELAYS.put(name, json.get(name).getAsFloat());
		}catch(Exception e){
			LOGGER.warn("cannot read delays: {}", e.toString());
		}
	}

	public static synchronized float get(String artist, String title) {
		if(!loaded) load();
		return DELAYS.getOrDefault(key(artist, title), 0.0F);
	}

	public static synchronized void set(String artist, String title, float delay) {
		if(!loaded) load();
		String name = key(artist, title);
		if(Math.abs(delay) < 0.05F) DELAYS.remove(name);
		else DELAYS.put(name, delay);
		save();
	}

	/** Clears saved per-song timing offsets when the user explicitly resets everything. */
	public static synchronized void clearAll() {
		DELAYS.clear();
		loaded = true;
		save();
	}

	private static void save() {
		try{
			Path path = file();
			Files.createDirectories(path.getParent());
			JsonObject json = new JsonObject();
			DELAYS.forEach(json::addProperty);
			Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
		}catch(IOException e){
			LOGGER.warn("cannot save delays: {}", e.toString());
		}
	}

	private LyricDelays() {
	}
}
