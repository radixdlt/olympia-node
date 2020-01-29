/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
