package org.radix.serialization2.client;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerConstants;
import org.radix.serialization2.SerializerDummy;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class with serializable fields for version and serializer
 * that can be inherited to easily implement those fields in
 * a standard way for DSON and JSON output.
 */
public class SerializableObject {
	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;
}
