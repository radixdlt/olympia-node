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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.HashCode;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Jackson {@link RadixObjectMapperConfigurator} that will serialize and deserialize
 * to the subset of <a href="http://cbor.io/">CBOR</a> that DSON uses.
 */
public class JacksonCborMapper extends ObjectMapper {
	private static final long serialVersionUID = 4917479892309630214L;

	public static JacksonCborMapper create(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties) {
		return new JacksonCborMapper(idLookup, filterProvider, sortProperties, Optional.empty());
	}

	/**
	 * Create an {@link RadixObjectMapperConfigurator} that will serialize to/from
	 * CBOR encoded DSON.
	 *
	 * @param idLookup A {@link SerializerIds} used to perform serializer
	 * 		ID lookup
	 * @param filterProvider A {@link FilterProvider} to use for filtering
	 * 		serialized fields
	 * @param serializationModifier optional BeanSerializerModifier to mix in the serialization
	 * @return A freshly created {@link JacksonCborMapper}
	 */
	public static JacksonCborMapper create(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties,
										   Optional<BeanSerializerModifier> serializationModifier) {
		return new JacksonCborMapper(idLookup, filterProvider, sortProperties, serializationModifier);
	}

	private JacksonCborMapper(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties,
							  Optional<BeanSerializerModifier> serializationModifier) {
		super(new RadixCBORFactory());
		RadixObjectMapperConfigurator.configure(this, idLookup, filterProvider, sortProperties);
		SimpleModule cborModule = new SimpleModule();

		cborModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		cborModule.addSerializer(EUID.class, new JacksonCborObjectBytesSerializer<>(
				EUID.class,
				JacksonCodecConstants.EUID_VALUE,
				EUID::toByteArray
		));
		cborModule.addSerializer(HashCode.class, new JacksonCborObjectBytesSerializer<>(
				HashCode.class,
				JacksonCodecConstants.HASH_VALUE,
				HashCode::asBytes
		));
		cborModule.addSerializer(byte[].class, new JacksonCborObjectBytesSerializer<>(
				byte[].class,
				JacksonCodecConstants.BYTES_VALUE,
				Function.identity()
		));
		cborModule.addSerializer(RadixAddress.class, new JacksonCborObjectBytesSerializer<>(
				RadixAddress.class,
				JacksonCodecConstants.ADDR_VALUE,
				RadixAddress::toByteArray
		));
		cborModule.addSerializer(UInt256.class, new JacksonCborObjectBytesSerializer<>(
				UInt256.class,
				JacksonCodecConstants.U20_VALUE,
				UInt256::toByteArray
		));
		cborModule.addSerializer(UInt384.class, new JacksonCborObjectBytesSerializer<>(
				UInt384.class,
				JacksonCodecConstants.U30_VALUE,
				UInt384::toByteArray
		));
		cborModule.addSerializer(RRI.class, new JacksonCborObjectBytesSerializer<>(
				RRI.class,
				JacksonCodecConstants.RRI_VALUE,
				id -> id.toString().getBytes(RadixConstants.STANDARD_CHARSET)
		));
		cborModule.addSerializer(AID.class, new JacksonCborObjectBytesSerializer<>(
				AID.class,
				JacksonCodecConstants.AID_VALUE,
				AID::getBytes
		));
		cborModule.addSerializer(long[].class, new JacksonCborObjectBytesSerializer<>(
				long[].class,
				JacksonCodecConstants.LONGS_VALUE,
				Longs::toBytes
		));

		cborModule.addKeySerializer(AID.class, new StdSerializer<AID>(AID.class) {
			@Override
			public void serialize(AID value, JsonGenerator gen, SerializerProvider provider) throws IOException {
				gen.writeFieldName(JacksonCodecConstants.AID_STR_VALUE + value.toString());
			}
		});

		cborModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		cborModule.addDeserializer(EUID.class, new JacksonCborObjectBytesDeserializer<>(
				EUID.class,
				JacksonCodecConstants.EUID_VALUE,
				EUID::new
		));
		cborModule.addDeserializer(HashCode.class, new JacksonCborObjectBytesDeserializer<>(
				HashCode.class,
				JacksonCodecConstants.HASH_VALUE,
				HashCode::fromBytes
		));
		cborModule.addDeserializer(byte[].class, new JacksonCborObjectBytesDeserializer<>(
				byte[].class,
				JacksonCodecConstants.BYTES_VALUE,
				Function.identity()
		));
		cborModule.addDeserializer(RadixAddress.class, new JacksonCborObjectBytesDeserializer<>(
				RadixAddress.class,
				JacksonCodecConstants.ADDR_VALUE,
				RadixAddress::from
		));
		cborModule.addDeserializer(UInt256.class, new JacksonCborObjectBytesDeserializer<>(
				UInt256.class,
				JacksonCodecConstants.U20_VALUE,
				UInt256::from
		));
		cborModule.addDeserializer(UInt384.class, new JacksonCborObjectBytesDeserializer<>(
				UInt384.class,
				JacksonCodecConstants.U30_VALUE,
				UInt384::from
		));
		cborModule.addDeserializer(RRI.class, new JacksonCborObjectBytesDeserializer<>(
				RRI.class,
				JacksonCodecConstants.RRI_VALUE,
				b -> RRI.from(new String(b, RadixConstants.STANDARD_CHARSET))
		));
		cborModule.addDeserializer(AID.class, new JacksonCborObjectBytesDeserializer<>(
				AID.class,
				JacksonCodecConstants.AID_VALUE,
				AID::from
		));
		cborModule.addDeserializer(long[].class, new JacksonCborObjectBytesDeserializer<>(
				long[].class,
				JacksonCodecConstants.LONGS_VALUE,
				Longs::fromBytes
		));
		cborModule.addKeyDeserializer(AID.class, new KeyDeserializer() {
			@Override
			public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
				if (!key.startsWith(JacksonCodecConstants.AID_STR_VALUE)) {
					throw new InvalidFormatException(ctxt.getParser(), "Expecting prefix" + JacksonCodecConstants.AID_STR_VALUE, key, AID.class);
				}
				return AID.from(key.substring(JacksonCodecConstants.STR_VALUE_LEN));
			}
		});

		serializationModifier.ifPresent(cborModule::setSerializerModifier);

		registerModule(cborModule);
	}
}
