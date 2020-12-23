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
			new ClassScanningSerializationPolicy(
					ImmutableList.of(OrderedPropertyBean.class, OrderedPropertyBean2.class)
			) { }
	);

	@Test
	public void propertyOrderingIsCorrect() {
		var inputBean =
				new OrderedPropertyBean("1", "2", "3", "4").propertyC("5");

		var serialized = serialization.toDson(inputBean, Output.ALL);

		// Deciphered expected value (remaining parts are omitted since they're irrelevant for test):
		// (leading character contains encoded length, a = 1, j = 10, v = 22)
		// aa -> field name "a"
		// a3 -> field content "3"
		// ab -> field name "b"
		// a1 -> field content "1"
		// ac -> field name "c"
		// a5 -> field content "5"
		// jserializer -> field name "serializer"
		// vtest.property_ordering -> field content "test.property_ordering"
		// ax -> field name "x"
		// a4 -> field content "4"
		// az -> field name "z"
		// a2 -> field content "2"
		assertEquals(">aaa3aba1aca5jserializervtest.property_orderingaxa4aza2~", toText(serialized));
	}

	@Test
	public void propertyOrderingForDerivedBeanIsCorrect() {
		var inputBean =
				new OrderedPropertyBean2("1", "2", "3", "4").propertyG("6");

		var serialized = serialization.toDson(inputBean, Output.ALL);

		//Deciphered expected value (remaining parts are omitted since they're irrelevant for test):
		// (leading character(s) contains encoded length, a = 1, j = 10, x? = 31)
		// aa -> field name "a"
		// a3 -> field content "3"
		// ab -> field name "b"
		// a2 -> field content "2"
		// ac -> field name "c"
		// a1 -> field content "1"
		// af -> field name "f"
		// a4 -> field content "4"
		// ag -> field name "g"
		// a6 -> field content "6"
		// jserializer -> field name "serializer"
		// x?test.extended_property_ordering -> field content "test.extended_property_ordering"
		assertEquals(">aaa3aba2aca1afa4aga6jserializerx?test.extended_property_ordering~", toText(serialized));
	}

	//Somewhat artificial transformation to keep output within printable range
	private static String toText(byte[] input) {
		var output = new char[input.length];

		for (int i = 0; i < input.length; i++) {
			output[i] = input[i] > 0 ? input[i] < 32 ? '?' : (char) input[i] : (char) (127 + input[i]);
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

	public abstract static class AbstractOrderedPropertyBean {
		@JsonProperty("a")
		@DsonOutput(Output.ALL)
		private final String propertyA;

		@JsonProperty("c")
		@DsonOutput(Output.ALL)
		private final String propertyC;

		@JsonProperty("b")
		@DsonOutput(Output.ALL)
		private final String propertyB;

		public AbstractOrderedPropertyBean(String propertyA, String propertyC, String propertyB) {
			this.propertyA = propertyA;
			this.propertyC = propertyC;
			this.propertyB = propertyB;
		}
	}

	@SerializerId2("test.extended_property_ordering")
	public static class OrderedPropertyBean2 extends AbstractOrderedPropertyBean {
		@JsonProperty(SerializerConstants.SERIALIZER_NAME)
		@DsonOutput(Output.ALL)
		private SerializerDummy serializer = SerializerDummy.DUMMY;

		@JsonProperty("f")
		@DsonOutput(Output.ALL)
		private final String propertyF;

		@JsonProperty("g")
		@DsonOutput(Output.ALL)
		private String propertyG;

		@JsonCreator
		public OrderedPropertyBean2(
				@JsonProperty("b") String propertyB,
				@JsonProperty("c") String propertyC,
				@JsonProperty("a") String propertyA,
				@JsonProperty("f") String propertyF
		) {
			super(propertyA, propertyB, propertyC);
			this.propertyF = propertyF;
		}

		public OrderedPropertyBean2 propertyG(String propertyG) {
			this.propertyG = propertyG;
			return this;
		}
	}
}