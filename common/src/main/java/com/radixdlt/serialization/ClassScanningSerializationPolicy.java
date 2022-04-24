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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.serialization.DsonOutput.Output;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class that maintains a map of {@link DsonOutput.Output} types to a set of pairs of classes and
 * field/method names to output for that serialization type.
 *
 * <p>This {@link SerializationPolicy} operates by scanning a supplied list of classes.
 */
public abstract class ClassScanningSerializationPolicy implements SerializationPolicy {

  private final EnumMap<Output, ImmutableMap<Class<?>, ImmutableSet<String>>> outputs =
      new EnumMap<>(Output.class);

  /**
   * Scan for all classes with an {@code SerializerId} annotation. The entire classpath is scanned,
   * including JAR files.
   *
   * @param classes The list of classes to scan for serialization annotations
   * @throws IllegalStateException If issues with serialization configuration are found while
   *     scanning.
   */
  protected ClassScanningSerializationPolicy(Collection<Class<?>> classes) {
    Map<Output, Map<Class<?>, Set<String>>> tempOutputs = new EnumMap<>(Output.class);
    // These are the outputs we will be collecting.
    // ALL and NONE are replaced with the complete set and empty set respectively
    tempOutputs.put(Output.HASH, new HashMap<>());
    tempOutputs.put(Output.API, new HashMap<>());
    tempOutputs.put(Output.WIRE, new HashMap<>());
    tempOutputs.put(Output.PERSIST, new HashMap<>());

    // First fields
    for (Class<?> outerCls : classes) {
      for (Class<?> cls = outerCls; !Object.class.equals(cls); cls = cls.getSuperclass()) {
        for (Field field : cls.getDeclaredFields()) {
          DsonOutput dsonOutput = field.getDeclaredAnnotation(DsonOutput.class);
          JsonProperty jsonProperty = field.getDeclaredAnnotation(JsonProperty.class);
          if (dsonOutput == null && jsonProperty != null) {
            throw new IllegalStateException(
                String.format(
                    "Field %s#%s has a %s annotation, but no %s annotation",
                    outerCls.getName(),
                    field.getName(),
                    JsonProperty.class.getSimpleName(),
                    DsonOutput.class.getSimpleName()));
          }
          if (dsonOutput != null && jsonProperty == null) {
            throw new IllegalStateException(
                String.format(
                    "Field %s#%s has a %s annotation, but no %s annotation",
                    outerCls.getName(),
                    field.getName(),
                    DsonOutput.class.getSimpleName(),
                    JsonProperty.class.getSimpleName()));
          }
          if (dsonOutput != null && jsonProperty != null) {
            String fieldName = jsonProperty.value();
            for (DsonOutput.Output out :
                DsonOutput.Output.toEnumSet(dsonOutput.value(), dsonOutput.include())) {
              if (!tempOutputs
                  .get(out)
                  .computeIfAbsent(outerCls, k -> new HashSet<>())
                  .add(fieldName)) {
                throw new IllegalStateException(
                    String.format(
                        "Duplicate property %s in class %s", fieldName, outerCls.getName()));
              }
            }
          }
        }
        // Now methods
        for (Method method : cls.getDeclaredMethods()) {
          DsonOutput dsonOutput = method.getDeclaredAnnotation(DsonOutput.class);
          JsonProperty jsonProperty = method.getDeclaredAnnotation(JsonProperty.class);
          JsonAnyGetter jsonAnyGetter = method.getDeclaredAnnotation(JsonAnyGetter.class);
          if (dsonOutput == null && jsonProperty != null) {
            if (method.getParameterCount() == 1) {
              // Ignore setter
              continue;
            }
            throw new IllegalStateException(
                String.format(
                    "Method %s#%s has a %s annotation, but no %s annotation",
                    outerCls.getName(),
                    method.getName(),
                    JsonProperty.class.getSimpleName(),
                    DsonOutput.class.getSimpleName()));
          }
          if (dsonOutput != null && jsonProperty == null && jsonAnyGetter == null) {
            throw new IllegalStateException(
                String.format(
                    "Method %s#%s has a %s annotation, but no %s or %s annotation",
                    outerCls.getName(),
                    method.getName(),
                    DsonOutput.class.getSimpleName(),
                    JsonProperty.class.getSimpleName(),
                    JsonAnyGetter.class.getSimpleName()));
          }
          if (dsonOutput != null && jsonProperty != null) {
            String fieldName = jsonProperty.value();
            if (method.getParameterCount() != 0) {
              throw new IllegalStateException(
                  String.format(
                      "Property %s in class %s not a getter", fieldName, outerCls.getName()));
            }
            for (DsonOutput.Output out :
                DsonOutput.Output.toEnumSet(dsonOutput.value(), dsonOutput.include())) {
              if (!tempOutputs
                  .get(out)
                  .computeIfAbsent(outerCls, k -> new HashSet<>())
                  .add(fieldName)) {
                throw new IllegalStateException(
                    String.format(
                        "Duplicate property %s in class %s", fieldName, outerCls.getName()));
              }
            }
          }
          if (dsonOutput != null && jsonAnyGetter != null) {
            DsonAnyProperties properties = method.getDeclaredAnnotation(DsonAnyProperties.class);
            if (properties == null) {
              throw new IllegalStateException(
                  String.format(
                      "Found %s annotation without %s annotation in class %s",
                      JsonAnyGetter.class.getSimpleName(),
                      DsonAnyProperties.class.getSimpleName(),
                      cls.getName()));
            }
            Set<String> fieldNames = ImmutableSet.copyOf(properties.value());
            for (DsonOutput.Output out :
                DsonOutput.Output.toEnumSet(dsonOutput.value(), dsonOutput.include())) {
              Set<String> fields =
                  tempOutputs.get(out).computeIfAbsent(outerCls, k -> new HashSet<>());
              for (String fieldName : fieldNames) {
                if (!fields.add(fieldName)) {
                  throw new IllegalStateException(
                      String.format(
                          "Duplicate property %s in class %s", fieldName, outerCls.getName()));
                }
              }
            }
          }
        }
      }
    }
    Map<Output, ImmutableMap<Class<?>, ImmutableSet<String>>> newOutputs =
        new EnumMap<>(Output.class);
    Map<Class<?>, Set<String>> classFields = new HashMap<>();
    for (Map.Entry<Output, Map<Class<?>, Set<String>>> output : tempOutputs.entrySet()) {
      newOutputs.put(output.getKey(), toImmutableMap(output.getValue()));
      for (Map.Entry<Class<?>, Set<String>> fields : output.getValue().entrySet()) {
        classFields
            .computeIfAbsent(fields.getKey(), k -> new HashSet<>())
            .addAll(fields.getValue());
      }
    }
    List<String> classesWithMissingSerializer =
        classFields.entrySet().stream()
            .filter(e -> !e.getValue().contains(SerializerConstants.SERIALIZER_NAME))
            .map(Map.Entry::getKey)
            .map(Class::getName)
            .sorted()
            .toList();
    if (!classesWithMissingSerializer.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "The following class%s missing the '%s' field: %s",
              classesWithMissingSerializer.size() == 1 ? " is" : "es are",
              SerializerConstants.SERIALIZER_NAME,
              String.join(", ", classesWithMissingSerializer)));
    }
    outputs.putAll(newOutputs);
  }

  @Override
  public ImmutableMap<Class<?>, ImmutableSet<String>> getIncludedFields(Output output) {
    ImmutableMap<Class<?>, ImmutableSet<String>> includedFields = outputs.get(output);
    if (includedFields == null) {
      throw new IllegalArgumentException("No such output selection: " + output);
    }
    return includedFields;
  }

  private static ImmutableMap<Class<?>, ImmutableSet<String>> toImmutableMap(
      Map<Class<?>, Set<String>> value) {
    ImmutableMap.Builder<Class<?>, ImmutableSet<String>> mapBuilder = ImmutableMap.builder();
    for (Map.Entry<Class<?>, Set<String>> e : value.entrySet()) {
      mapBuilder.put(e.getKey(), ImmutableSet.copyOf(e.getValue()));
    }
    return mapBuilder.build();
  }
}
