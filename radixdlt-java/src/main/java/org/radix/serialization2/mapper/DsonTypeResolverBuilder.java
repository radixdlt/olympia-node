package org.radix.serialization2.mapper;

import java.util.Collection;

import org.radix.serialization2.SerializerIds;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

/**
 * TypeResolverBuilder that outputs type information for all classes that
 * are part of the serializable class set.  This set consists of all classes
 * annotated with {@link org.radix.serialization2.SerializerId2} and their
 * superclasses.
 */
class DsonTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
	private static final long serialVersionUID = 29L;

	private final SerializerIds idLookup;

	public DsonTypeResolverBuilder(SerializerIds idLookup) {
		super(ObjectMapper.DefaultTyping.NON_FINAL);
		init(Id.CUSTOM, null).inclusion(As.EXISTING_PROPERTY).typeProperty("serializer");
		this.idLookup = idLookup;
	}

    @Override
    public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
    	// Serialization handled already
    	return null;
    }

    @Override
	public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
    	return super.buildTypeDeserializer(config, baseType, subtypes);
    }

	@Override
	public boolean useForType(JavaType t) {
		return idLookup.isSerializableSuper(t.getRawClass());
	}

    @Override
	protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType, Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
		return new DsonTypeIdResolver(baseType, config.getTypeFactory(), idLookup);
	}
}
