/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.HashCode;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import java.io.IOException;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;

/**
 * A Jackson {@link RadixObjectMapperConfigurator} that will serialize and deserialize to the JSON
 * in the format that Radix requires.
 */
public class JacksonJsonMapper extends ObjectMapper {
  private static final long serialVersionUID = 4917479892309630214L;

  public static JacksonJsonMapper create(
      SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties) {
    return new JacksonJsonMapper(idLookup, filterProvider, sortProperties, Optional.empty());
  }

  /**
   * Create an {@link RadixObjectMapperConfigurator} that will serialize to/from the JSON format
   * that radix requires.
   *
   * @param idLookup A {@link SerializerIds} used to perform serializer ID lookup
   * @param filterProvider A {@link FilterProvider} to use for filtering serialized fields
   * @param sortProperties {@code true} if JSON output properties should be sorted in
   *     lexicographical order
   * @param serializationModifier optional BeanSerializerModifier to mix in the serialization
   * @return A freshly created {@link JacksonJsonMapper}
   */
  public static JacksonJsonMapper create(
      SerializerIds idLookup,
      FilterProvider filterProvider,
      boolean sortProperties,
      Optional<BeanSerializerModifier> serializationModifier) {
    return new JacksonJsonMapper(idLookup, filterProvider, sortProperties, serializationModifier);
  }

  private JacksonJsonMapper(
      SerializerIds idLookup,
      FilterProvider filterProvider,
      boolean sortProperties,
      Optional<BeanSerializerModifier> serializationModifier) {
    super(new JsonFactory());
    RadixObjectMapperConfigurator.configure(this, idLookup, filterProvider, sortProperties);
    SimpleModule jsonModule = new SimpleModule();
    jsonModule.addSerializer(
        EUID.class,
        new JacksonJsonObjectStringSerializer<>(
            EUID.class, JacksonCodecConstants.EUID_STR_VALUE, EUID::toString));
    jsonModule.addSerializer(HashCode.class, new JacksonJsonHashCodeSerializer());
    jsonModule.addSerializer(byte[].class, new JacksonJsonBytesSerializer());
    jsonModule.addSerializer(String.class, new JacksonJsonStringSerializer());
    jsonModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
    jsonModule.addSerializer(
        UInt256.class,
        new JacksonJsonObjectStringSerializer<>(
            UInt256.class, JacksonCodecConstants.U20_STR_VALUE, UInt256::toString));
    jsonModule.addSerializer(
        UInt384.class,
        new JacksonJsonObjectStringSerializer<>(
            UInt384.class, JacksonCodecConstants.U30_STR_VALUE, UInt384::toString));
    jsonModule.addSerializer(
        REAddr.class,
        new JacksonJsonObjectStringSerializer<>(
            REAddr.class,
            JacksonCodecConstants.RRI_STR_VALUE,
            rri -> Hex.toHexString(rri.getBytes())));
    jsonModule.addSerializer(
        AID.class,
        new JacksonJsonObjectStringSerializer<>(
            AID.class, JacksonCodecConstants.AID_STR_VALUE, AID::toString));

    jsonModule.addKeySerializer(
        AID.class,
        new StdSerializer<AID>(AID.class) {
          @Override
          public void serialize(AID value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            gen.writeFieldName(JacksonCodecConstants.AID_STR_VALUE + value.toString());
          }
        });

    jsonModule.addDeserializer(
        EUID.class,
        new JacksonJsonObjectStringDeserializer<>(
            EUID.class, JacksonCodecConstants.EUID_STR_VALUE, EUID::new));
    jsonModule.addDeserializer(HashCode.class, new JacksonJsonHashCodeDeserializer());
    jsonModule.addDeserializer(byte[].class, new JacksonJsonBytesDeserializer());
    jsonModule.addDeserializer(String.class, new JacksonJsonStringDeserializer());
    jsonModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
    jsonModule.addDeserializer(
        UInt256.class,
        new JacksonJsonObjectStringDeserializer<>(
            UInt256.class, JacksonCodecConstants.U20_STR_VALUE, UInt256::from));
    jsonModule.addDeserializer(
        UInt384.class,
        new JacksonJsonObjectStringDeserializer<>(
            UInt384.class, JacksonCodecConstants.U30_STR_VALUE, UInt384::from));
    jsonModule.addDeserializer(
        REAddr.class,
        new JacksonJsonObjectStringDeserializer<>(
            REAddr.class, JacksonCodecConstants.RRI_STR_VALUE, s -> REAddr.of(Hex.decode(s))));
    jsonModule.addDeserializer(
        AID.class,
        new JacksonJsonObjectStringDeserializer<>(
            AID.class, JacksonCodecConstants.AID_STR_VALUE, AID::from));
    jsonModule.addKeyDeserializer(
        AID.class,
        new KeyDeserializer() {
          @Override
          public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            if (!key.startsWith(JacksonCodecConstants.AID_STR_VALUE)) {
              throw new InvalidFormatException(
                  ctxt.getParser(),
                  "Expecting prefix" + JacksonCodecConstants.AID_STR_VALUE,
                  key,
                  AID.class);
            }
            return AID.from(key.substring(JacksonCodecConstants.STR_VALUE_LEN));
          }
        });

    // Special modifier for Enum values to remove :str: leadin from front
    jsonModule.setDeserializerModifier(
        new BeanDeserializerModifier() {
          @Override
          @SuppressWarnings("rawtypes")
          public JsonDeserializer<Enum> modifyEnumDeserializer(
              DeserializationConfig config,
              final JavaType type,
              BeanDescription beanDesc,
              final JsonDeserializer<?> deserializer) {
            return new JsonDeserializer<>() {
              @Override
              @SuppressWarnings("unchecked")
              public Enum deserialize(JsonParser jp, DeserializationContext ctxt)
                  throws IOException {
                String name = jp.getValueAsString();
                if (!name.startsWith(JacksonCodecConstants.STR_STR_VALUE)) {
                  throw new IllegalStateException(
                      String.format(
                          "Expected value starting with %s, found: %s",
                          JacksonCodecConstants.STR_STR_VALUE, name));
                }
                Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
                return Enum.valueOf(
                    rawClass, jp.getValueAsString().substring(JacksonCodecConstants.STR_VALUE_LEN));
              }
            };
          }
        });

    serializationModifier.ifPresent(jsonModule::setSerializerModifier);

    registerModule(jsonModule);
  }
}
