package com.donutgamble.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Notes, tags and UUIDs the streamer keeps about players. Saved as config/donutgamble/players.json. */
public class PlayerDirectory {
	public static class Profile {
		public String name;
		/** The Minecraft UUID, when the player was seen in the tab list; null otherwise. */
		public String uuid;
		public String notes = "";
		public List<String> tags = new ArrayList<>();
	}

	public static final int MAX_TAG = 20;

	public Map<String, Profile> profiles = new LinkedHashMap<>();

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public Profile get(String name) {
		return name == null ? null : profiles.get(key(name));
	}

	public Profile getOrCreate(String name) {
		return profiles.computeIfAbsent(key(name), k -> {
			Profile p = new Profile();
			p.name = name;
			return p;
		});
	}

	/** Records a UUID; returns true if it was new or changed. */
	public boolean setUuid(String name, String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return false;
		}
		Profile p = getOrCreate(name);
		if (uuid.equals(p.uuid)) {
			return false;
		}
		p.uuid = uuid;
		return true;
	}

	public void setNotes(String name, String notes) {
		getOrCreate(name).notes = notes == null ? "" : notes.trim();
	}

	/** Adds a tag (trimmed, at most {@value #MAX_TAG} characters, no duplicates ignoring case). */
	public boolean addTag(String name, String tag) {
		if (tag == null || tag.isBlank()) {
			return false;
		}
		String t = tag.trim();
		if (t.length() > MAX_TAG) {
			t = t.substring(0, MAX_TAG);
		}
		Profile p = getOrCreate(name);
		for (String existing : p.tags) {
			if (existing.equalsIgnoreCase(t)) {
				return false;
			}
		}
		p.tags.add(t);
		return true;
	}

	public boolean removeTag(String name, String tag) {
		Profile p = get(name);
		return p != null && p.tags.removeIf(t -> t.equalsIgnoreCase(tag));
	}

	public List<String> tags(String name) {
		Profile p = get(name);
		return p == null ? List.of() : p.tags;
	}
}
