package com.radixdlt.serialization.mapper;

import java.util.function.Function;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRI;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import com.radixdlt.common.AID;
import org.radix.time.Timestamps;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;

/**
 * A Jackson {@link ObjectMapper} that will serialize and deserialize
 * to the subset of <a href="http://cbor.io/">CBOR</a> that DSON uses.
 */
public class JacksonCborMapper extends ObjectMapper {

	private static final long serialVersionUID = 4917479892309630214L;

	private JacksonCborMapper() {
		super(createCborFactory());
	}

	private static JsonFactory createCborFactory() {
		return new RadixCBORFactory();
	}

	/**
	 * Create an {@link ObjectMapper} that will serialize to/from
	 * CBOR encoded DSON.
	 *
	 * @param idLookup A {@link SerializerIds} used to perform serializer
	 * 		ID lookup
	 * @param filterProvider A {@link FilterProvider} to use for filtering
	 * 		serialized fields
	 * @return A freshly created {@link JacksonCborMapper}
	 */
	public static JacksonCborMapper create(SerializerIds idLookup, FilterProvider filterProvider) {
		SimpleModule cborModule = new SimpleModule();

		cborModule.addSerializer(Timestamps.class, new JacksonTimestampsSerializer());
		cborModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		cborModule.addSerializer(EUID.class, new JacksonCborObjectBytesSerializer<>(
			EUID.class,
			JacksonCodecConstants.EUID_VALUE,
			EUID::toByteArray
		));
		cborModule.addSerializer(Hash.class, new JacksonCborObjectBytesSerializer<>(
			Hash.class,
			JacksonCodecConstants.HASH_VALUE,
			Hash::toByteArray
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

		cborModule.addDeserializer(Timestamps.class, new JacksonTimestampsDeserializer());
		cborModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		cborModule.addDeserializer(EUID.class, new JacksonCborObjectBytesDeserializer<>(
			EUID.class,
			JacksonCodecConstants.EUID_VALUE,
			EUID::new
		));
		cborModule.addDeserializer(Hash.class, new JacksonCborObjectBytesDeserializer<>(
			Hash.class,
			JacksonCodecConstants.HASH_VALUE,
			Hash::new
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

		JacksonCborMapper mapper = new JacksonCborMapper();
		mapper.registerModule(cborModule);
	    mapper.registerModule(new JsonOrgModule());
	    mapper.registerModule(new GuavaModule());

		mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY));
		mapper.setSerializationInclusion(Include.NON_EMPTY);
		mapper.setFilterProvider(filterProvider);
		mapper.setAnnotationIntrospector(new DsonFilteringIntrospector());
	    mapper.setDefaultTyping(new DsonTypeResolverBuilder(idLookup));
		return mapper;
	}
}
