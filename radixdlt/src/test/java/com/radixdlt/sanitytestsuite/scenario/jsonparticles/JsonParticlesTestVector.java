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

package com.radixdlt.sanitytestsuite.scenario.jsonparticles;

import com.google.gson.JsonObject;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.sanitytestsuite.model.SanityTestVector;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.JSONFormatter;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public class JsonParticlesTestVector implements SanityTestVector {

	public static final class Expected {
		public String hashOfJSON;
	}

	public static final class Input {
		public static final class MetaData {
			private String objectTypeSerializer;

			private String serializerFormat;

			public DsonOutput.Output output() {
				return DsonOutput.Output.valueOf(this.serializerFormat);
			}

			@SuppressWarnings("unchecked")
			public Class<Particle> particleClass() {
				return (Class<Particle>) DefaultSerialization.getInstance().getClassForId(this.objectTypeSerializer);
			}
		}


		public String jsonString() {
			return JSONFormatter.sortPrettyPrintJSONString(json.toString());
		}

		private JsonObject json;
		public MetaData metaData;
	}

	public Expected expected;
	public Input input;
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier