package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerIds;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Special converter for {@link SerializerDummy} class.
 */
class JacksonSerializerDummySerializer extends StdSerializer<SerializerDummy> {
	private static final long serialVersionUID = -2472482347700365657L;
	private final SerializerIds idLookup;

	JacksonSerializerDummySerializer(SerializerIds idLookup) {
		this(null, idLookup);
	}

	JacksonSerializerDummySerializer(Class<SerializerDummy> t, SerializerIds idLookup) {
		super(t);
		this.idLookup = idLookup;
	}

	@Override
	public void serialize(SerializerDummy value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		Object parent = jgen.getOutputContext().getCurrentValue();
		String id = idLookup.getIdForClass(parent.getClass());
		if (id == null) {
			throw new IllegalStateException("Can't find ID for class: " + parent.getClass().getName());
		}
		jgen.writeString(id);
	}

    @Override
	public void serializeWithType(SerializerDummy value, JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer)
			throws IOException {
    	serialize(value, jgen, provider);
    }
}