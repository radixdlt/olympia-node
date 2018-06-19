package com.radixdlt.client.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.radixdlt.client.core.address.RadixAddress;

public class RadixMessageContent {
	private final static JsonDeserializer<RadixAddress> addressDeserializer = (json, typeOf, context) -> new RadixAddress(json.getAsString());
	private final static JsonSerializer<RadixAddress> addressSerializer = (src, typeOf, context) -> new JsonPrimitive(src.toString());

	private final static Gson gson = new GsonBuilder()
		.registerTypeAdapter(RadixAddress.class, addressDeserializer)
		.registerTypeAdapter(RadixAddress.class, addressSerializer)
		.create();

	private final RadixAddress to;
	private final RadixAddress from;
	private final String content;

	public RadixMessageContent(RadixAddress to, RadixAddress from, String content) {
		this.to = to;
		this.from = from;
		this.content = content;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public String getContent() {
		return content;
	}

	public String toJson() {
		return gson.toJson(this);
	}

	public static RadixMessageContent fromJson(String json) {
		return gson.fromJson(json, RadixMessageContent.class);
	}

	@Override
	public String toString() {
		return from + " -> " + to + ": " + content;
	}
}
