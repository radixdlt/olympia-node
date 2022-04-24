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

package com.radixdlt.serialization;

import static com.radixdlt.serialization.SerializerConstants.SERIALIZER_ID_ANNOTATION;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.radixdlt.identifiers.EUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that maintains a map of serializer IDs to {@code Class<?>} objects and vice versa.
 *
 * <p>This {@link SerializerIds} operates by scanning a supplied list of classes.
 */
public abstract class ClassScanningSerializerIds implements SerializerIds {
  private static final Logger log = LogManager.getLogger(ClassScanningSerializerIds.class);

  // Assuming that lookups from class to ID will be more identifiers
  private final Map<Class<?>, String> classIdMap = Maps.newHashMap();
  // Inverse view of same data
  private final BiMap<String, Class<?>> idClassMap = HashBiMap.create();

  private final HashSet<Class<?>> serializableSupertypes = new HashSet<>();

  /**
   * Scan for all classes with an {@code SerializerId} annotation in the specified set of classes.
   *
   * @param classes The list of classes to scan for serialization annotations
   * @throws SerializerIdsException If two or more classes are found with the same {@code
   *     SerializerId}
   */
  protected ClassScanningSerializerIds(Collection<Class<?>> classes) {
    Map<String, List<Class<?>>> polymorphicMap = new HashMap<>();

    for (Class<?> cls : classes) {
      SerializerId2 sid = cls.getDeclaredAnnotation(SERIALIZER_ID_ANNOTATION);
      if (sid == null) {
        // For some reason, Reflections returns classes without SerializerId, but
        // that inherit from classes with the (non-inheritable) annotation.  Sad.
        log.debug("Skipping unannotated class " + cls.getName());
        continue;
      }

      if (cls.isInterface()) {
        // Interfaces should not be marked with @SerializerId
        log.warn(
            String.format(
                "Skipping interface %s with unexpected %s annotation",
                cls.getName(), SERIALIZER_ID_ANNOTATION.getSimpleName()));
        continue;
      }

      String id = sid.value();

      if (Polymorphic.class.isAssignableFrom(cls)) {
        // Polymorphic class hierarchy checked later
        log.debug("Polymorphic class:" + cls.getName() + " with ID:" + id);
        polymorphicMap.computeIfAbsent(id, k -> new ArrayList<>()).add(cls);
      } else {
        // Check for duplicates
        Class<?> dupClass = idClassMap.put(id, cls);
        if (dupClass != null) {
          throw new SerializerIdsException(
              String.format(
                  "Aborting, duplicate ID %s discovered in classes: [%s, %s]",
                  id, cls.getName(), dupClass.getName()));
        }
        log.debug("Putting Class:" + cls.getName() + " with ID:" + id);
        collectSupertypes(cls);
        collectInterfaces(cls);
      }
    }

    classIdMap.putAll(idClassMap.inverse());
    Map<EUID, String> idNumericMap = new HashMap<>();
    // Check polymorphic hierarchy consistency
    for (Map.Entry<String, List<Class<?>>> entry : polymorphicMap.entrySet()) {
      String id = entry.getKey();
      if (!idClassMap.containsKey(id)) {
        throw new SerializerIdsException(
            String.format(
                "No concrete class with ID '%s' for polymorphic classes %s",
                entry.getKey(), entry.getValue()));
      }
      EUID numericId = SerializationUtils.stringToNumericID(id);
      String dupNumericId = idNumericMap.put(numericId, id);
      if (dupNumericId != null) {
        throw new SerializerIdsException(
            String.format(
                "Aborting, numeric id %s of %s clashes with %s", numericId, id, dupNumericId));
      }
      for (Class<?> cls : entry.getValue()) {
        String dupId = classIdMap.put(cls, id);
        if (dupId != null) {
          throw new SerializerIdsException(
              String.format(
                  "Aborting, class %s has duplicate IDs %s and %s", cls.getName(), id, dupId));
        }
      }
    }
  }

  private void collectSupertypes(Class<?> cls) {
    while (!Object.class.equals(cls)) {
      serializableSupertypes.add(cls);
      cls = cls.getSuperclass();
    }
  }

  private void collectInterfaces(Class<?> cls) {
    Stream.of(cls.getInterfaces())
        .filter(this::isSerializerRoot)
        .forEachOrdered(serializableSupertypes::add);
  }

  private boolean isSerializerRoot(Class<?> clazz) {
    return clazz.isAnnotationPresent(SerializerConstants.SERIALIZER_ROOT_ANNOTATION);
  }

  @Override
  public String getIdForClass(Class<?> cls) {
    return classIdMap.get(cls);
  }

  @Override
  public Class<?> getClassForId(String id) {
    return idClassMap.get(id);
  }

  @Override
  public boolean isSerializableSuper(Class<?> cls) {
    return serializableSupertypes.contains(cls);
  }
}
