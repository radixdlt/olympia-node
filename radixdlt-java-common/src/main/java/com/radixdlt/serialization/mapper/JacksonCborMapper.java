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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
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
import com.radixdlt.utils.Instants;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Jackson {@link RadixObjectMapperConfigurator} that will serialize and deserialize to the subset
 * of <a href="http://cbor.io/">CBOR</a> that DSON uses.
 */
public class JacksonCborMapper extends ObjectMapper {
  private static final long serialVersionUID = 4917479892309630214L;

  public static JacksonCborMapper create(
      SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties) {
    return new JacksonCborMapper(idLookup, filterProvider, sortProperties, Optional.empty());
  }

  /**
   * Create an {@link RadixObjectMapperConfigurator} that will serialize to/from CBOR encoded DSON.
   *
   * @param idLookup A {@link SerializerIds} used to perform serializer ID lookup
   * @param filterProvider A {@link FilterProvider} to use for filtering serialized fields
   * @param serializationModifier optional BeanSerializerModifier to mix in the serialization
   * @return A freshly created {@link JacksonCborMapper}
   */
  public static JacksonCborMapper create(
      SerializerIds idLookup,
      FilterProvider filterProvider,
      boolean sortProperties,
      Optional<BeanSerializerModifier> serializationModifier) {
    return new JacksonCborMapper(idLookup, filterProvider, sortProperties, serializationModifier);
  }

  private JacksonCborMapper(
      SerializerIds idLookup,
      FilterProvider filterProvider,
      boolean sortProperties,
      Optional<BeanSerializerModifier> serializationModifier) {
    super(new RadixCBORFactory());
    RadixObjectMapperConfigurator.configure(this, idLookup, filterProvider, sortProperties);
    var cborModule = new SimpleModule();

    cborModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
    cborModule.addSerializer(
        EUID.class,
        new JacksonCborObjectBytesSerializer<>(
            EUID.class, JacksonCodecConstants.EUID_VALUE, EUID::toByteArray));
    cborModule.addSerializer(
        HashCode.class,
        new JacksonCborObjectBytesSerializer<>(
            HashCode.class, JacksonCodecConstants.HASH_VALUE, HashCode::asBytes));
    cborModule.addSerializer(
        byte[].class,
        new JacksonCborObjectBytesSerializer<>(
            byte[].class, JacksonCodecConstants.BYTES_VALUE, Function.identity()));
    cborModule.addSerializer(
        UInt256.class,
        new JacksonCborObjectBytesSerializer<>(
            UInt256.class, JacksonCodecConstants.U20_VALUE, UInt256::toByteArray));
    cborModule.addSerializer(
        UInt384.class,
        new JacksonCborObjectBytesSerializer<>(
            UInt384.class, JacksonCodecConstants.U30_VALUE, UInt384::toByteArray));
    cborModule.addSerializer(
        REAddr.class,
        new JacksonCborObjectBytesSerializer<>(
            REAddr.class, JacksonCodecConstants.RRI_VALUE, REAddr::getBytes));
    cborModule.addSerializer(
        AID.class,
        new JacksonCborObjectBytesSerializer<>(
            AID.class, JacksonCodecConstants.AID_VALUE, AID::getBytes));
    cborModule.addSerializer(
        long[].class,
        new JacksonCborObjectBytesSerializer<>(
            long[].class, JacksonCodecConstants.LONGS_VALUE, Longs::toBytes));
    cborModule.addSerializer(
        Instant.class,
        new JacksonCborObjectBytesSerializer<>(
            Instant.class, JacksonCodecConstants.INSTANT_VALUE, Instants::toBytes));

    cborModule.addKeySerializer(
        AID.class,
        new StdSerializer<AID>(AID.class) {
          @Override
          public void serialize(AID value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            gen.writeFieldName(JacksonCodecConstants.AID_STR_VALUE + value.toString());
          }
        });

    cborModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
    cborModule.addDeserializer(
        EUID.class,
        new JacksonCborObjectBytesDeserializer<>(
            EUID.class, JacksonCodecConstants.EUID_VALUE, EUID::new));
    cborModule.addDeserializer(
        HashCode.class,
        new JacksonCborObjectBytesDeserializer<>(
            HashCode.class, JacksonCodecConstants.HASH_VALUE, HashCode::fromBytes));
    cborModule.addDeserializer(
        byte[].class,
        new JacksonCborObjectBytesDeserializer<>(
            byte[].class, JacksonCodecConstants.BYTES_VALUE, Function.identity()));
    cborModule.addDeserializer(
        UInt256.class,
        new JacksonCborObjectBytesDeserializer<>(
            UInt256.class, JacksonCodecConstants.U20_VALUE, UInt256::from));
    cborModule.addDeserializer(
        UInt384.class,
        new JacksonCborObjectBytesDeserializer<>(
            UInt384.class, JacksonCodecConstants.U30_VALUE, UInt384::from));
    cborModule.addDeserializer(
        REAddr.class,
        new JacksonCborObjectBytesDeserializer<>(
            REAddr.class, JacksonCodecConstants.RRI_VALUE, REAddr::of));
    cborModule.addDeserializer(
        AID.class,
        new JacksonCborObjectBytesDeserializer<>(
            AID.class, JacksonCodecConstants.AID_VALUE, AID::from));
    cborModule.addDeserializer(
        long[].class,
        new JacksonCborObjectBytesDeserializer<>(
            long[].class, JacksonCodecConstants.LONGS_VALUE, Longs::fromBytes));
    cborModule.addDeserializer(
        Instant.class,
        new JacksonCborObjectBytesDeserializer<>(
            Instant.class, JacksonCodecConstants.INSTANT_VALUE, Instants::fromBytes));
    cborModule.addKeyDeserializer(
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

    serializationModifier.ifPresent(cborModule::setSerializerModifier);

    registerModule(cborModule);
  }
}
