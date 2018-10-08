package com.radixdlt.client.core.serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.util.Base64Encoded;

import static com.radixdlt.client.core.serialization.SerializationConstants.BYT_PREFIX;
import static com.radixdlt.client.core.serialization.SerializationConstants.HSH_PREFIX;
import static com.radixdlt.client.core.serialization.SerializationConstants.STR_PREFIX;
import static com.radixdlt.client.core.serialization.SerializationConstants.UID_PREFIX;

public class Dson {
	private enum Primitive {
		NUMBER(0x20),
		EUID(0x21),
		HASH(0x22),
		BYTES(0x40),
		STRING(0x41),
		ARRAY(0x80),
		OBJECT(0x81);

		private final int value;

		Primitive(int value) {
			this.value = value;
		}
	}

	private static byte[] longToByteArray(long value) {
		byte[] result = new byte[8];

		for (int i = 7; i >= 0; i--) {
			result[i] = (byte) (value & 0xffL);
			value >>= 8;
		}

		return result;
	}

	private static final Dson DSON = new Dson();

	public static Dson getInstance() {
		return DSON;
	}

	private Dson() {
	}

	private JsonElement parse(ByteBuffer byteBuffer) {
		int type = byteBuffer.get() & 0xFF;
		int length = SerializationUtils.decodeInt(byteBuffer);
		final JsonElement result;
		if (type == Primitive.NUMBER.value) {
			result = new JsonPrimitive(byteBuffer.getLong());
		} else if (type == Primitive.STRING.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			result = new JsonPrimitive(STR_PREFIX + new String(buffer, StandardCharsets.UTF_8));
		} else if (type == Primitive.BYTES.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			result = new JsonPrimitive(BYT_PREFIX + Base64.toBase64String(buffer));
		} else if (type == Primitive.OBJECT.value) {
			JsonObject jsonObject = new JsonObject();

			while (length > 0) {
				int fieldNameLength = byteBuffer.get() & 0xFF;
				byte[] fieldName = new byte[fieldNameLength];
				byteBuffer.get(fieldName);
				int start = byteBuffer.position();
				JsonElement child = parse(byteBuffer);
				int end = byteBuffer.position();
				final int fieldLength = 1 + fieldNameLength + (end - start);
				length -= fieldLength;
				jsonObject.add(new String(fieldName), child);
			}

			result = jsonObject;
		} else if (type == Primitive.ARRAY.value) {
			JsonArray jsonArray = new JsonArray();
			while (length > 0) {
				int start = byteBuffer.position();
				JsonElement child = parse(byteBuffer);
				int end = byteBuffer.position();
				length -= (end - start);
				jsonArray.add(child);
			}
			result = jsonArray;
		} else if (type == Primitive.EUID.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			result = new JsonPrimitive(UID_PREFIX + Hex.toHexString(buffer));
		} else if (type == Primitive.HASH.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			result = new JsonPrimitive(HSH_PREFIX + Hex.toHexString(buffer));
		} else {
			throw new RuntimeException("Unknown type: " + type);
		}

		return result;
	}

	public JsonElement parse(byte[] buffer) {
		return parse(ByteBuffer.wrap(buffer));
	}

	private interface DsonField {
		String getName();
		byte[] getBytes();
	}

	private DsonField versionField = new DsonField() {
		@Override
		public String getName() {
			return "version";
		}

		@Override
		public byte[] getBytes() {
			return toDson(100L);
		}
	};

	private Collector<DsonField, ?, byte[]> toByteArray = Collectors.collectingAndThen(Collectors.toList(), list -> {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		list.forEach(dsonField -> {
			try {
				byte[] nameBytes = dsonField.getName().getBytes(StandardCharsets.UTF_8);
				SerializationUtils.encodeInt(nameBytes.length, outputStream);
				outputStream.write(nameBytes);
				outputStream.write(dsonField.getBytes());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		return outputStream.toByteArray();
	});

	public byte[] toDson(Object o) {
		final byte[] raw;
		final int type;

		if (o == null) {
			throw new IllegalArgumentException("Null sent");
		} else if (o instanceof Collection) {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			Collection<?> collection = (Collection<?>) o;
			for (Object arrayObject : collection) {
				try {
					byte[] arrayObjRaw = toDson(arrayObject);
					outputStream.write(arrayObjRaw);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			raw = outputStream.toByteArray();
			type = Primitive.ARRAY.value;
		} else if (o instanceof Long) {
			raw = longToByteArray((Long) o);
			type = Primitive.NUMBER.value;
		} else if (o instanceof Number) {
			throw new IllegalStateException("A number must be a long to be serialized in Dson: " + o);
		} else if (o instanceof EUID) {
			raw = ((EUID) o).toByteArray();
			type = Primitive.EUID.value;
		} else if (o instanceof Base64Encoded) {
			raw = ((Base64Encoded) o).toByteArray();
			type = Primitive.BYTES.value;
		} else if (o instanceof String) {
			raw = ((String) o).getBytes(StandardCharsets.UTF_8);
			type = Primitive.STRING.value;
		} else if (o instanceof byte[]) {
			raw = (byte[]) o;
			type = Primitive.BYTES.value;
		} else if (o instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) o;

			if (HashMap.class == o.getClass()) {
				throw new IllegalStateException("Cannot DSON serialize HashMap. Must be a predictably ordered map.");
			}

			Stream<DsonField> fieldStream = map.entrySet().stream().map(e -> new DsonField() {
				@Override
				public String getName() {
					return e.getKey().toString();
				}

				@Override
				public byte[] getBytes() {
					return toDson(e.getValue());
				}
			});

			raw = fieldStream
				.sorted(Comparator.comparing(DsonField::getName))
				.collect(toByteArray);
			type = Primitive.OBJECT.value;

		} else if (o instanceof HasOrdinalValue) { // HACK
			raw = longToByteArray(((HasOrdinalValue) o).ordinalValue());
			type = 2;
		} else {
			Class<?> c = o.getClass();
			List<Field> fields = new ArrayList<>();
			while (c != Object.class) {
				fields.addAll(Arrays.asList(c.getDeclaredFields()));
				c = c.getSuperclass();
			}

			Stream<DsonField> fieldStream = fields.stream()
				.filter(field -> !field.getName().equalsIgnoreCase("signatures"))
				.filter(field -> !field.getName().equalsIgnoreCase("serialVersionUID"))
				.filter(field -> !field.getName().equalsIgnoreCase("spin")) // TODO: This needs to be added back in
				.filter(field -> !Modifier.isTransient(field.getModifiers()))
				.filter(field -> {
					try {
						field.setAccessible(true);
						return field.get(o) != null;
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				})
				.map(field -> dsonFieldFrom(o, field));

			raw = Stream.concat(fieldStream, Stream.of(versionField))
				.sorted(Comparator.comparing(DsonField::getName))
				.collect(toByteArray);
			type = Primitive.OBJECT.value;
		}

		ByteBuffer byteBuffer = ByteBuffer.allocate(1 + SerializationUtils.intLength(raw.length) + raw.length);
		byteBuffer.put((byte) type);
		SerializationUtils.encodeInt(raw.length, byteBuffer);
		byteBuffer.put(raw);

		return byteBuffer.array();
	}

	private DsonField dsonFieldFrom(Object o, Field field) {
		SerializedName serializedName = field.getAnnotation(SerializedName.class);
		String name = (serializedName == null) ? field.getName() : serializedName.value();

		return new DsonField() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public byte[] getBytes() {
				try {
					field.setAccessible(true);
					Object fieldObject = field.get(o);
					return toDson(fieldObject);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}
