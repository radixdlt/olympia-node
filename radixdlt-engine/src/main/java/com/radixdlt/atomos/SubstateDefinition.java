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

package com.radixdlt.atomos;

import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.KeyDeserializer;
import com.radixdlt.constraintmachine.KeySerializer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserializer;
import com.radixdlt.constraintmachine.SubstateSerializer;
import com.radixdlt.constraintmachine.VirtualMapper;
import com.radixdlt.serialization.DeserializeException;
import java.util.stream.Stream;

/**
 * Defines how to retrieve important properties from a given particle type.
 *
 * @param <T> the particle class
 */
public final class SubstateDefinition<T extends Particle> {
  private final Class<T> substateClass;
  private final SubstateTypeId typeId;
  private final SubstateDeserializer<T> deserializer;
  private final SubstateSerializer<T> serializer;

  private final KeyDeserializer keyDeserializer;
  private final KeySerializer keySerializer;
  private final VirtualMapper<T> virtualMapper;

  private static final KeyDeserializer KEY_DESERIALIZER =
      buf -> {
        throw new DeserializeException("Virtual substate not supported");
      };
  private static final KeySerializer KEY_SERIALIZER =
      (k, buf) -> {
        throw new IllegalStateException("Cannot create key");
      };
  private static final VirtualMapper<?> VIRTUAL_MAPPER =
      o -> {
        throw new IllegalStateException("Cannot virtualize");
      };

  private SubstateDefinition(
      Class<T> substateClass,
      SubstateTypeId typeId,
      SubstateDeserializer<T> deserializer,
      SubstateSerializer<T> serializer,
      KeyDeserializer keyDeserializer,
      KeySerializer keySerializer,
      VirtualMapper<T> virtualMapper) {
    this.substateClass = substateClass;
    this.typeId = typeId;
    this.deserializer = deserializer;
    this.serializer = serializer;
    this.keyDeserializer = keyDeserializer;
    this.keySerializer = keySerializer;
    this.virtualMapper = virtualMapper;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Particle> SubstateDefinition<T> create(
      Class<T> substateClass,
      SubstateTypeId typeId,
      SubstateDeserializer<T> deserializer,
      SubstateSerializer<T> serializer) {

    return create(
        substateClass,
        typeId,
        deserializer,
        serializer,
        KEY_DESERIALIZER,
        KEY_SERIALIZER,
        (VirtualMapper<T>) VIRTUAL_MAPPER);
  }

  @SuppressWarnings("unchecked")
  public static <T extends Particle> SubstateDefinition<T> create(
      Class<T> substateClass,
      SubstateTypeId typeId,
      SubstateDeserializer<T> deserializer,
      SubstateSerializer<T> serializer,
      KeySerializer keySerializer) {

    return create(
        substateClass,
        typeId,
        deserializer,
        serializer,
        KEY_DESERIALIZER,
        keySerializer,
        (VirtualMapper<T>) VIRTUAL_MAPPER);
  }

  public static <T extends Particle> SubstateDefinition<T> create(
      Class<T> substateClass,
      SubstateTypeId typeId,
      SubstateDeserializer<T> deserializer,
      SubstateSerializer<T> serializer,
      KeyDeserializer keyDeserializer,
      KeySerializer keySerializer,
      VirtualMapper<T> virtualMapper) {

    return new SubstateDefinition<>(
        substateClass,
        typeId,
        deserializer,
        serializer,
        keyDeserializer,
        keySerializer,
        virtualMapper);
  }

  @SuppressWarnings("unchecked")
  private SubstateDefinition<? extends Particle> withClass(Class<? extends Particle> clazz) {
    return create(
        (Class<T>) clazz,
        typeId,
        deserializer,
        serializer,
        keyDeserializer,
        keySerializer,
        virtualMapper);
  }

  public byte typeByte() {
    return typeId.id();
  }

  public Class<T> substateClass() {
    return substateClass;
  }

  public SubstateSerializer<T> serializer() {
    return serializer;
  }

  public SubstateDeserializer<T> deserializer() {
    return deserializer;
  }

  public KeySerializer keySerializer() {
    return keySerializer;
  }

  public KeyDeserializer keyDeserializer() {
    return keyDeserializer;
  }

  public VirtualMapper<T> virtualMapper() {
    return virtualMapper;
  }

  @SuppressWarnings("unchecked")
  public static Stream<SubstateDefinition<? extends Particle>> expandToSubclasses(
      SubstateDefinition<? extends Particle> definition) {
    var clazz = definition.substateClass;
    var classes = clazz.getPermittedSubclasses();

    if (classes == null) {
      return Stream.of(definition);
    }

    return Stream.concat(Stream.of(clazz), Stream.of(classes))
        .map(cls -> (Class<? extends Particle>) cls)
        .map(definition::withClass);
  }
}
