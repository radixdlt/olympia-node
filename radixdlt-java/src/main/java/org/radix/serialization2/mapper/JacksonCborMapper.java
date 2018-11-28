package org.radix.serialization2.mapper;

import java.util.function.Function;

import org.radix.common.ID.EUID;
import org.radix.crypto.Hash;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerIds;
import org.radix.time.Timestamps;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.util.Base58;

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
		return new CBORFactory();
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
		cborModule.addSerializer(EUID.class, new JacksonCborEUIDSerializer());
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
			addr -> Base58.fromBase58(addr.toString())
		));
		cborModule.addSerializer(UInt256.class, new JacksonCborObjectBytesSerializer<>(
			UInt256.class,
			JacksonCodecConstants.U20_VALUE,
			UInt256::toByteArray
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
