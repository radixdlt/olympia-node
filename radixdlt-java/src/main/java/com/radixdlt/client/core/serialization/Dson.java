package com.radixdlt.client.core.serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.util.Base64Encoded;

import okio.ByteString;

public class Dson {
	private enum Primitive {
		NUMBER(2),
		STRING(3),
		BYTES(4),
		OBJECT(5),
		ARRAY(6),
		EUID(7),
		HASH(8);

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
		int type = byteBuffer.get();
		int length = byteBuffer.getInt();
		final JsonElement result;
		if (type == Primitive.NUMBER.value) {
			result = new JsonPrimitive(byteBuffer.getLong());
		} else if (type == Primitive.STRING.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			result = new JsonPrimitive(new String(buffer));
		} else if (type == Primitive.BYTES.value) {
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("serializer", "BASE64");
			jsonObject.addProperty("value", Base64.toBase64String(buffer));
			result = jsonObject;
		} else if (type == Primitive.OBJECT.value) {
			JsonObject jsonObject = new JsonObject();

			while (length > 0) {
				int fieldNameLength = byteBuffer.get();
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
			JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("serializer", "EUID");
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			jsonObject.addProperty("value", new BigInteger(buffer).toString());
			result = jsonObject;
		} else if (type == Primitive.HASH.value) {
			JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("serializer", "HASH");
			byte[] buffer = new byte[length];
			byteBuffer.get(buffer);
			jsonObject.addProperty("value", ByteString.of(buffer).hex());
			result = jsonObject;
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
				outputStream.write(nameBytes.length);
				outputStream.write(nameBytes);
				outputStream.write(dsonField.getBytes());
			} catch (IOException e) {
				throw new RuntimeException();
			}
		});
		return outputStream.toByteArray();
	});

	public byte[] toDson(Object o) {
		final byte[] raw;
		final byte type;

		if (o == null) {
			throw new IllegalArgumentException("Null sent");
		} else if (o instanceof Collection) {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			Collection collection = (Collection) o;
			for (Object arrayObject : collection) {
				try {
					byte[] arrayObjRaw = toDson(arrayObject);
					outputStream.write(arrayObjRaw);
				} catch (IOException e) {
					throw new RuntimeException();
				}
			}
			raw = outputStream.toByteArray();
			type = 6;
		} else if (o instanceof Long) {
			raw = longToByteArray((Long) o);
			type = 2;
		} else if (o instanceof EUID) {
			raw = ((EUID) o).toByteArray();
			type = 7;
		} else if (o instanceof Base64Encoded) {
			raw = ((Base64Encoded) o).toByteArray();
			type = 4;
		} else if (o instanceof String) {
			raw = ((String) o).getBytes();
			type = 3;
		} else if (o instanceof byte[]) {
			raw = (byte[]) o;
			type = 4;
		} else if (o instanceof Map) {

			final Map<?, ?> map = (Map) o;
			Stream<DsonField> fieldStream = map.keySet().stream().map(key -> new DsonField() {
				@Override
				public String getName() {
					return key.toString();
				}

				@Override
				public byte[] getBytes() {
					return toDson(map.get(key));
				}
			});

			raw = fieldStream
				.sorted(Comparator.comparing(DsonField::getName))
				.collect(toByteArray);
			type = 5;

		} else {
			Class c = o.getClass();
			List<Field> fields = new ArrayList<>();
			while (c != Object.class) {
				fields.addAll(Arrays.asList(c.getDeclaredFields()));
				c = c.getSuperclass();
			}

			Stream<DsonField> fieldStream = fields.stream()
				.filter(field -> !field.getName().equalsIgnoreCase("signatures"))
				.filter(field -> !field.getName().equalsIgnoreCase("serialVersionUID"))
				.filter(field -> !Modifier.isTransient(field.getModifiers()))
				.filter(field -> {
					try {
						field.setAccessible(true);
						return field.get(o) != null;
					} catch (IllegalAccessException e) {
						throw new RuntimeException();
					}
				})
				.map(field -> new DsonField() {
					@Override
					public String getName() {
						SerializedName serializedName = field.getAnnotation(SerializedName.class);
						return serializedName == null ? field.getName() : serializedName.value();
					}

					@Override
					public byte[] getBytes() {
						try {
							field.setAccessible(true);
							Object fieldObject = field.get(o);
							return toDson(fieldObject);
						} catch (IllegalAccessException e) {
							throw new RuntimeException();
						}
					}
				});

			raw = Stream.concat(fieldStream, Stream.of(versionField))
				.sorted(Comparator.comparing(DsonField::getName))
				.collect(toByteArray);
			type = 5;
		}

		ByteBuffer byteBuffer = ByteBuffer.allocate(5 + raw.length);
		byteBuffer.put(type);
		byteBuffer.putInt(raw.length);
		byteBuffer.put(raw);

		return byteBuffer.array();
	}
}
