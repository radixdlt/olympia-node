package org.radix.serialization2.mapper;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.radix.common.ID.EUID;
import org.radix.crypto.Hash;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerIds;
import org.radix.time.Timestamps;

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
		cborModule.addSerializer(EUID.class, new JacksonCborEUIDSerializer());
		cborModule.addSerializer(Hash.class, new JacksonCborHashSerializer());
		cborModule.addSerializer(Timestamps.class, new JacksonTimestampsSerializer());
		cborModule.addSerializer(byte[].class, new JacksonCborBytesSerializer());
		cborModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		cborModule.addSerializer(RadixAddress.class, new JacksonCborObjectStringSerializer<>(
			JacksonCodecConstants.ADDR_VALUE, RadixAddress::toString)
		);

		cborModule.addDeserializer(EUID.class, new JacksonCborEUIDDeserializer());
		cborModule.addDeserializer(Hash.class, new JacksonCborHashDeserializer());
		cborModule.addDeserializer(Timestamps.class, new JacksonTimestampsDeserializer());
		cborModule.addDeserializer(byte[].class, new JacksonCborBytesDeserializer());
		cborModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		cborModule.addDeserializer(RadixAddress.class, new JacksonCborObjectStringDeserializer<>(
			RadixAddress.class, JacksonCodecConstants.ADDR_VALUE, RadixAddress::from)
		);

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
