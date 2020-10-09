package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.ClassScanningSerializationPolicy;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RadixObjectMapperConfiguratorTest {
	private final Serialization serialization = Serialization.create(
			ClasspathScanningSerializerIds.create(),
			new ClassScanningSerializationPolicy(ImmutableList.of(OrderedPropertyBean.class)) {
			}
	);

	@Test
	public void propertyOrderingIsCorrect() {
		var inputBean =
				new OrderedPropertyBean("b", "z", "a", "x").propertyC("c");

		var serialized = serialization.toDson(inputBean, Output.ALL);

		assertEquals(">aaaaababacacjserializervtest.property_orderingaxaxazaz~", toText(serialized));
	}

	//Somewhat artificial transformation to shift output into printable range
	private static String toText(byte[] input) {
		var output = new char[input.length];

		for (int i = 0; i < input.length; i++) {
			output[i] = input[i] > 0 ? (char) input[i] : (char) (127 + input[i]);
		}
		return new String(output);
	}

	@SerializerId2("test.property_ordering")
	public static class OrderedPropertyBean {
		@JsonProperty(SerializerConstants.SERIALIZER_NAME)
		@DsonOutput(Output.ALL)
		private SerializerDummy serializer = SerializerDummy.DUMMY;

		@JsonProperty("z")
		@DsonOutput(Output.ALL)
		private final String propertyZ;

		@JsonProperty("x")
		@DsonOutput(Output.ALL)
		private final String propertyX;

		@JsonProperty("a")
		@DsonOutput(Output.ALL)
		private final String propertyA;

		@JsonProperty("c")
		@DsonOutput(Output.ALL)
		private String propertyC;

		@JsonProperty("b")
		@DsonOutput(Output.ALL)
		private final String propertyB;

		@JsonCreator
		public OrderedPropertyBean(
				@JsonProperty("b") String propertyB,
				@JsonProperty("z") String propertyZ,
				@JsonProperty("a") String propertyA,
				@JsonProperty("x") String propertyX) {
			this.propertyZ = propertyZ;
			this.propertyX = propertyX;
			this.propertyA = propertyA;
			this.propertyB = propertyB;
		}

		public OrderedPropertyBean propertyC(String propertyC) {
			this.propertyC = propertyC;
			return this;
		}
	}
}