package com.radixdlt.client.core.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixUniverseType;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.DataParticle;
import com.radixdlt.client.core.atoms.Emission;
import com.radixdlt.client.core.atoms.EncryptorParticle;
import com.radixdlt.client.core.atoms.IdParticle;
import com.radixdlt.client.core.atoms.JunkParticle;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.util.Base64Encoded;
import com.radixdlt.client.core.util.Int128;
import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.util.encoders.Hex;

public class RadixJson {

	private static JsonObject serializedValue(String type, String value) {
		JsonObject element = new JsonObject();
		element.addProperty("serializer", type);
		element.addProperty("value", value);
		return element;
	}

	private static final JsonSerializer<Base64Encoded> BASE64_SERIALIZER =
		(src, typeOfSrc, context) -> serializedValue("BASE64", src.base64());

	private static final JsonDeserializer<Payload> PAYLOAD_DESERIALIZER =
		(json, typeOfT, context) -> Payload.fromBase64(json.getAsJsonObject().get("value").getAsString());

	private static final JsonDeserializer<ECPublicKey> PK_DESERIALIZER = (json, typeOf, context) -> {
		byte[] publicKey = Base64.decode(json.getAsJsonObject().get("value").getAsString());
		return new ECPublicKey(publicKey);
	};

	private static final JsonDeserializer<EncryptedPrivateKey> PROTECTOR_DESERIALIZER = (json, typeOf, context) -> {
		byte[] encryptedPrivateKey = Base64.decode(json.getAsJsonObject().get("value").getAsString());
		return new EncryptedPrivateKey(encryptedPrivateKey);
	};

	private static final JsonDeserializer<RadixUniverseType> UNIVERSER_TYPE_DESERIALIZER =
		(json, typeOf, context) -> RadixUniverseType.valueOf(json.getAsInt());

	private static final JsonDeserializer<NodeRunnerData> NODE_RUNNDER_DATA_JSON_DESERIALIZER = (json, typeOf, context) -> {
		return new NodeRunnerData(
			json.getAsJsonObject().has("host") ? json.getAsJsonObject().get("host").getAsJsonObject().get("ip").getAsString() : null,
			json.getAsJsonObject().get("system").getAsJsonObject().get("shards").getAsJsonObject().get("low").getAsLong(),
			json.getAsJsonObject().get("system").getAsJsonObject().get("shards").getAsJsonObject().get("high").getAsLong()
		);
	};

	private static final JsonSerializer<Particle> PARTICLE_SERIALIZER = (particle, typeOfT, context) -> {
		if (particle.getClass() == AtomFeeConsumable.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", -1463653224);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == JunkParticle.class) {

			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", -1123054001);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == Consumable.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", 318720611);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == Consumer.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", 214856694);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == Emission.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", 1782261127);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == IdParticle.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", "IDPARTICLE".hashCode());
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		}

		throw new RuntimeException("Unknown Particle: " + particle.getClass());
	};

	private static final JsonDeserializer<Particle> PARTICLE_DESERIALIZER = (json, typeOf, context) -> {
		long serializer = json.getAsJsonObject().get("serializer").getAsLong();
		if (serializer == -1463653224) {
			return context.deserialize(json.getAsJsonObject(), AtomFeeConsumable.class);
		} else if (serializer == 318720611) {
			return context.deserialize(json.getAsJsonObject(), Consumable.class);
		} else if (serializer == 214856694) {
			return context.deserialize(json.getAsJsonObject(), Consumer.class);
		} else if (serializer == 1782261127) {
			return context.deserialize(json.getAsJsonObject(), Emission.class);
		} else if (serializer == -1123054001) {
			return context.deserialize(json.getAsJsonObject(), JunkParticle.class);
		} else if (serializer == "IDPARTICLE".hashCode()) {
			return context.deserialize(json.getAsJsonObject(), IdParticle.class);
		} else {
			throw new RuntimeException("Unknown particle serializer: " + serializer);
		}
	};

	private static class EUIDSerializer implements JsonDeserializer<EUID>, JsonSerializer<EUID> {
		@Override
		public JsonElement serialize(EUID src, Type typeOfSrc, JsonSerializationContext context) {
			return serializedValue("EUID", src.toString());
		}

		@Override
		public EUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return new EUID(Int128.from(Hex.decode(json.getAsJsonObject().get("value").getAsString())));
		}
	}


	private static class ByteArraySerializer implements JsonDeserializer<byte[]>, JsonSerializer<byte[]> {
		@Override
		public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
			return serializedValue("BASE64", Base64.toBase64String(src));
		}

		@Override
		public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Base64.decode(json.getAsJsonObject().get("value").getAsString());
		}
	}

	private static final Map<Class, Integer> SERIALIZERS = new HashMap<>();
	static {
		SERIALIZERS.put(Atom.class, 2019665);
		SERIALIZERS.put(ECKeyPair.class, 547221307);
		SERIALIZERS.put(ECSignature.class, -434788200);
		SERIALIZERS.put(EncryptorParticle.class, 105401064);
		SERIALIZERS.put(DataParticle.class, 473758768);
	}

	private static final TypeAdapterFactory ECKEYPAIR_ADAPTER_FACTORY = new TypeAdapterFactory() {
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			final Integer serializer = SERIALIZERS.get(type.getRawType());
			if (serializer == null) {
				return null;
			}
			final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
			final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

			return new TypeAdapter<T>() {
				@Override
				public void write(JsonWriter out, T value) throws IOException {
					JsonElement tree = delegate.toJsonTree(value);
					if (!tree.isJsonNull()) {
						tree.getAsJsonObject().addProperty("serializer", serializer);
						tree.getAsJsonObject().addProperty("version", 100);
					}
					elementAdapter.write(out, tree);
				}

				@Override
				public T read(JsonReader in) throws IOException {
					JsonElement tree = elementAdapter.read(in);
					return delegate.fromJsonTree(tree);
				}
			};
		}
	};

	private static final Gson GSON;

	static {
		GsonBuilder gsonBuilder = new GsonBuilder()
			.registerTypeHierarchyAdapter(Base64Encoded.class, BASE64_SERIALIZER)
			.registerTypeAdapterFactory(ECKEYPAIR_ADAPTER_FACTORY)
			.registerTypeAdapter(byte[].class, new ByteArraySerializer())
			.registerTypeAdapter(Particle.class, PARTICLE_SERIALIZER)
			.registerTypeAdapter(Particle.class, PARTICLE_DESERIALIZER)
			.registerTypeAdapter(EUID.class, new EUIDSerializer())
			.registerTypeAdapter(Payload.class, PAYLOAD_DESERIALIZER)
			.registerTypeAdapter(EncryptedPrivateKey.class, PROTECTOR_DESERIALIZER)
			.registerTypeAdapter(ECPublicKey.class, PK_DESERIALIZER)
			.registerTypeAdapter(RadixUniverseType.class, UNIVERSER_TYPE_DESERIALIZER)
			.registerTypeAdapter(NodeRunnerData.class, NODE_RUNNDER_DATA_JSON_DESERIALIZER);

		GSON = gsonBuilder.create();
	}

	private RadixJson() {
	}

	public static Gson getGson() {
		return GSON;
	}
}
