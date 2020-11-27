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