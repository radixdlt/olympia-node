package org.radix.api;

import java.io.IOException;
import java.io.InputStream;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class AtomSchemas {
	private static final Schema schema;
	private static final JSONObject schemaJson;

	private AtomSchemas() {
	}

	static {
		try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("schemas/atom.schema.json")) {
			if (inputStream == null) {
				throw new RuntimeException("Cannot load schema");
			}

			schemaJson = new JSONObject(new JSONTokener(inputStream));
			schema = SchemaLoader.load(schemaJson);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public static String getJsonSchemaString(int indentFactor) {
		return schemaJson.toString(indentFactor);
	}

	public static Schema get() {
		return schema;
	}
}
