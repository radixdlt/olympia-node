/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.core.openapitools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-11-24T23:25:44.231186-06:00[America/Chicago]")
public class JSON {
  private ObjectMapper mapper;

  public JSON() {
    mapper = new ObjectMapper();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, true);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
  }

  /**
   * Set the date format for JSON (de)serialization with Date properties.
   * @param dateFormat Date format
   */
  public void setDateFormat(DateFormat dateFormat) {
    mapper.setDateFormat(dateFormat);
  }

  /**
   * Get the object mapper
   *
   * @return object mapper
   */
  public ObjectMapper getMapper() { return mapper; }

  /**
   * Returns the target model class that should be used to deserialize the input data.
   * The discriminator mappings are used to determine the target model class.
   *
   * @param node The input data.
   * @param modelClass The class that contains the discriminator mappings.
   */
  public static Class<?> getClassForElement(JsonNode node, Class<?> modelClass) {
    ClassDiscriminatorMapping cdm = modelDiscriminators.get(modelClass);
    if (cdm != null) {
      return cdm.getClassForElement(node, new HashSet<Class<?>>());
    }
    return null;
  }

  /**
   * Helper class to register the discriminator mappings.
   */
  private static class ClassDiscriminatorMapping {
    // The model class name.
    Class<?> modelClass;
    // The name of the discriminator property.
    String discriminatorName;
    // The discriminator mappings for a model class.
    Map<String, Class<?>> discriminatorMappings;

    // Constructs a new class discriminator.
    ClassDiscriminatorMapping(Class<?> cls, String propertyName, Map<String, Class<?>> mappings) {
      modelClass = cls;
      discriminatorName = propertyName;
      discriminatorMappings = new HashMap<String, Class<?>>();
      if (mappings != null) {
        discriminatorMappings.putAll(mappings);
      }
    }

    // Return the name of the discriminator property for this model class.
    String getDiscriminatorPropertyName() {
      return discriminatorName;
    }

    // Return the discriminator value or null if the discriminator is not
    // present in the payload.
    String getDiscriminatorValue(JsonNode node) {
      // Determine the value of the discriminator property in the input data.
      if (discriminatorName != null) {
        // Get the value of the discriminator property, if present in the input payload.
        node = node.get(discriminatorName);
        if (node != null && node.isValueNode()) {
          String discrValue = node.asText();
          if (discrValue != null) {
            return discrValue;
          }
        }
      }
      return null;
    }

    /**
     * Returns the target model class that should be used to deserialize the input data.
     * This function can be invoked for anyOf/oneOf composed models with discriminator mappings.
     * The discriminator mappings are used to determine the target model class.
     *
     * @param node The input data.
     * @param visitedClasses The set of classes that have already been visited.
     */
    Class<?> getClassForElement(JsonNode node, Set<Class<?>> visitedClasses) {
      if (visitedClasses.contains(modelClass)) {
        // Class has already been visited.
        return null;
      }
      // Determine the value of the discriminator property in the input data.
      String discrValue = getDiscriminatorValue(node);
      if (discrValue == null) {
        return null;
      }
      Class<?> cls = discriminatorMappings.get(discrValue);
      // It may not be sufficient to return this cls directly because that target class
      // may itself be a composed schema, possibly with its own discriminator.
      visitedClasses.add(modelClass);
      for (Class<?> childClass : discriminatorMappings.values()) {
        ClassDiscriminatorMapping childCdm = modelDiscriminators.get(childClass);
        if (childCdm == null) {
          continue;
        }
        if (!discriminatorName.equals(childCdm.discriminatorName)) {
          discrValue = getDiscriminatorValue(node);
          if (discrValue == null) {
            continue;
          }
        }
        if (childCdm != null) {
          // Recursively traverse the discriminator mappings.
          Class<?> childDiscr = childCdm.getClassForElement(node, visitedClasses);
          if (childDiscr != null) {
            return childDiscr;
          }
        }
      }
      return cls;
    }
  }

  /**
   * Returns true if inst is an instance of modelClass in the OpenAPI model hierarchy.
   *
   * The Java class hierarchy is not implemented the same way as the OpenAPI model hierarchy,
   * so it's not possible to use the instanceof keyword.
   *
   * @param modelClass A OpenAPI model class.
   * @param inst The instance object.
   */
  public static boolean isInstanceOf(Class<?> modelClass, Object inst, Set<Class<?>> visitedClasses) {
    if (modelClass.isInstance(inst)) {
      // This handles the 'allOf' use case with single parent inheritance.
      return true;
    }
    if (visitedClasses.contains(modelClass)) {
      // This is to prevent infinite recursion when the composed schemas have
      // a circular dependency.
      return false;
    }
    visitedClasses.add(modelClass);

    // Traverse the oneOf/anyOf composed schemas.
    Map<String, Class<?>> descendants = modelDescendants.get(modelClass);
    if (descendants != null) {
      for (Class<?> childType : descendants.values()) {
        if (isInstanceOf(childType, inst, visitedClasses)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A map of discriminators for all model classes.
   */
  private static Map<Class<?>, ClassDiscriminatorMapping> modelDiscriminators = new HashMap<>();

  /**
   * A map of oneOf/anyOf descendants for each model class.
   */
  private static Map<Class<?>, Map<String, Class<?>>> modelDescendants = new HashMap<>();

  /**
    * Register a model class discriminator.
    *
    * @param modelClass the model class
    * @param discriminatorPropertyName the name of the discriminator property
    * @param mappings a map with the discriminator mappings.
    */
  public static void registerDiscriminator(Class<?> modelClass, String discriminatorPropertyName, Map<String, Class<?>> mappings) {
    ClassDiscriminatorMapping m = new ClassDiscriminatorMapping(modelClass, discriminatorPropertyName, mappings);
    modelDiscriminators.put(modelClass, m);
  }

  /**
    * Register the oneOf/anyOf descendants of the modelClass.
    *
    * @param modelClass the model class
    * @param descendants a map of oneOf/anyOf descendants.
    */
  public static void registerDescendants(Class<?> modelClass, Map<String, Class<?>> descendants) {
    modelDescendants.put(modelClass, descendants);
  }

  private static JSON json;

  static {
    json = new JSON();
  }

  /**
    * Get the default JSON instance.
    *
    * @return the default JSON instance
    */
  public static JSON getDefault() {
    return json;
  }

  /**
    * Set the default JSON instance.
    *
    * @param json JSON instance to be used
    */
  public static void setDefault(JSON json) {
    JSON.json = json;
  }
}
