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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static com.radixdlt.serialization.SerializationTestUtils.testEncodeDecode;

import com.radixdlt.serialization.DsonOutput.Output;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Raft of tests for serialization of objects.
 *
 * <p>Note that the tests that round-trip types through the serializer are not run for {@link
 * Polymorphic} types, as these types do not serialize to themselves, but to one of their
 * superclasses.
 *
 * @param <T> The type under test.
 */
public abstract class SerializeObject<T> extends RadixTest {
  @BeforeClass
  public static void serializeObjectBeforeClass() {
    // Disable this output for now, as the serialiser is quite verbose when starting.
    Configurator.setLevel(
        LogManager.getLogger(ClassScanningSerializerIds.class).getName(), Level.INFO);
  }

  private final Class<T> cls;
  private final Supplier<T> factory;

  protected SerializeObject(Class<T> cls, Supplier<T> factory) {
    this.cls = cls;
    this.factory = factory;
  }

  @Test
  public void testObjectHasEquals() throws NoSuchMethodException {
    Method method = factory.get().getClass().getMethod("equals", Object.class);
    assertFalse(method.getDeclaringClass().equals(Object.class));
  }

  @Test
  public void testObjectHasHashCode() throws NoSuchMethodException {
    Method method = factory.get().getClass().getMethod("hashCode");
    assertFalse(method.getDeclaringClass().equals(Object.class));
  }

  @Test
  public void testNONEIsEmpty() {
    String s2Json = getSerialization().toJson(factory.get(), Output.NONE);
    assertEquals("{}", s2Json);
  }

  @Test
  public void testRoundTripJsonSame() throws DeserializeException {
    checkPolymorphic();
    Serialization s = getSerialization();
    T initialObj = factory.get();
    String initialJson = s.toJson(initialObj, Output.ALL);
    T deserialisedObj = s.fromJson(initialJson, this.cls);
    assertEquals(initialObj, deserialisedObj);
  }

  @Test
  public void testRoundTripDsonSame() throws DeserializeException {
    checkPolymorphic();
    Serialization s = getSerialization();
    T initialObj = factory.get();
    byte[] initialDson = s.toDson(initialObj, Output.ALL);
    T deserialisedObj = s.fromDson(initialDson, this.cls);
    assertEquals(initialObj, deserialisedObj);
  }

  @Test
  public void testEncodeDecodeALL() throws Exception {
    checkPolymorphic();
    testEncodeDecode(factory.get(), cls, getSerialization(), Output.ALL);
  }

  @Test
  public void testEncodeDecodeAPI() throws Exception {
    checkPolymorphic();
    testEncodeDecode(factory.get(), cls, getSerialization(), Output.API);
  }

  // Output.HASH does not serialize "serializers" and can't be deserialized
  // Output.NONE does not serialize "serializers" and can't be deserialized

  @Test
  public void testEncodeDecodePERSIST() throws Exception {
    checkPolymorphic();
    testEncodeDecode(factory.get(), cls, getSerialization(), Output.PERSIST);
  }

  @Test
  public void testEncodeDecodeWIRE() throws Exception {
    checkPolymorphic();
    testEncodeDecode(factory.get(), cls, getSerialization(), Output.WIRE);
  }

  private void checkPolymorphic() {
    assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
  }
}
