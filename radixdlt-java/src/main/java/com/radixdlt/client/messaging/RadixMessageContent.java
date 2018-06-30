package com.radixdlt.client.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.radixdlt.client.core.address.RadixAddress;

public class RadixMessageContent {
	private static final JsonDeserializer<RadixAddress> ADDRESS_DESERIALIZER = (json, typeOf, context) -> new RadixAddress(json.getAsString());
	private static final JsonSerializer<RadixAddress> ADDRESS_SERIALIZER = (src, typeOf, context) -> new JsonPrimitive(src.toString());

	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(RadixAddress.class, ADDRESS_DESERIALIZER)
		.registerTypeAdapter(RadixAddress.class, ADDRESS_SERIALIZER)
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
		return GSON.toJson(this);
	}

	public static RadixMessageContent fromJson(String json) {
		return GSON.fromJson(json, RadixMessageContent.class);
	}

	@Override
	public String toString() {
		return from + " -> " + to + ": " + content;
	}
}
