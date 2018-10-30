package org.radix.serialization2.client;

import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Hopefully temporary class to provide conversion from
 * org.json JSONObject class to Gson JsonElement.
 */
public final class GsonJson {

	private static class Holder {
		private static GsonJson instance = new GsonJson();

		// This paraphernalia is here to placate checkstyle
		static GsonJson instance() {
			return instance;
		}
	}

	public static GsonJson getInstance() {
		return Holder.instance();
	}

	private final Gson gson;
	private final JsonParser parser;

	private GsonJson() {
		this.gson = new Gson();
		this.parser = new JsonParser();
	}

	public JSONObject fromGson(JsonElement element) {
		return new JSONObject(this.gson.toJson(element));
	}

	public String stringFromGson(JsonElement element) {
		return this.gson.toJson(element);
	}

	public JsonElement toGson(JSONObject element) {
		return this.parser.parse(element.toString());
	}
}
