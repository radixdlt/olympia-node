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

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.ClassScanningSerializationPolicy;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.junit.Test;

public class RadixObjectMapperConfiguratorTest {
  private final Serialization serialization =
      Serialization.create(
          ClasspathScanningSerializerIds.create(),
          new ClassScanningSerializationPolicy(
              ImmutableList.of(OrderedPropertyBean.class, OrderedPropertyBean2.class)) {});

  @Test
  public void propertyOrderingIsCorrect() {
    var inputBean = new OrderedPropertyBean("1", "2", "3", "4").propertyC("5");

    var serialized = serialization.toDson(inputBean, Output.ALL);

    // Deciphered expected value (remaining parts are omitted since they're irrelevant for test):
    // (leading character contains encoded length, a = 1, j = 10, v = 22)
    // aa -> field name "a"
    // a3 -> field content "3"
    // ab -> field name "b"
    // a1 -> field content "1"
    // ac -> field name "c"
    // a5 -> field content "5"
    // bsz -> field name "bsz"
    // vtest.property_ordering -> field content "test.property_ordering"
    // ax -> field name "x"
    // a4 -> field content "4"
    // az -> field name "z"
    // a2 -> field content "2"
    assertEquals(">aaa3aba1aca5bszvtest.property_orderingaxa4aza2~", toText(serialized));
  }

  @Test
  public void propertyOrderingForDerivedBeanIsCorrect() {
    var inputBean = new OrderedPropertyBean2("1", "2", "3", "4").propertyG("6");

    var serialized = serialization.toDson(inputBean, Output.ALL);

    // Deciphered expected value (remaining parts are omitted since they're irrelevant for test):
    // (leading character(s) contains encoded length, a = 1, j = 10, x? = 31)
    // aa -> field name "a"
    // a3 -> field content "3"
    // ab -> field name "b"
    // a2 -> field content "2"
    // ac -> field name "c"
    // a1 -> field content "1"
    // af -> field name "f"
    // a4 -> field content "4"
    // ag -> field name "g"
    // a6 -> field content "6"
    // bsz -> field name "sz"
    // x?test.extended_property_ordering -> field content "test.extended_property_ordering"
    assertEquals(">aaa3aba2aca1afa4aga6bszx?test.extended_property_ordering~", toText(serialized));
  }

  // Somewhat artificial transformation to keep output within printable range
  private static String toText(byte[] input) {
    var output = new char[input.length];

    for (int i = 0; i < input.length; i++) {
      output[i] = input[i] > 0 ? input[i] < 32 ? '?' : (char) input[i] : (char) (127 + input[i]);
    }
    return new String(output);
  }

  @SerializerId2("test.property_ordering")
  public static class OrderedPropertyBean {
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(Output.ALL)
    private SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("z")
    @DsonOutput(Output.ALL)
    private final String propertyZ;

    @JsonProperty("x")
    @DsonOutput(Output.ALL)
    private final String propertyX;

    @JsonProperty("a")
    @DsonOutput(Output.ALL)
    private final String propertyA;

    @JsonProperty("c")
    @DsonOutput(Output.ALL)
    private String propertyC;

    @JsonProperty("b")
    @DsonOutput(Output.ALL)
    private final String propertyB;

    @JsonCreator
    public OrderedPropertyBean(
        @JsonProperty("b") String propertyB,
        @JsonProperty("z") String propertyZ,
        @JsonProperty("a") String propertyA,
        @JsonProperty("x") String propertyX) {
      this.propertyZ = propertyZ;
      this.propertyX = propertyX;
      this.propertyA = propertyA;
      this.propertyB = propertyB;
    }

    public OrderedPropertyBean propertyC(String propertyC) {
      this.propertyC = propertyC;
      return this;
    }
  }

  public abstract static class AbstractOrderedPropertyBean {
    @JsonProperty("a")
    @DsonOutput(Output.ALL)
    private final String propertyA;

    @JsonProperty("c")
    @DsonOutput(Output.ALL)
    private final String propertyC;

    @JsonProperty("b")
    @DsonOutput(Output.ALL)
    private final String propertyB;

    public AbstractOrderedPropertyBean(String propertyA, String propertyC, String propertyB) {
      this.propertyA = propertyA;
      this.propertyC = propertyC;
      this.propertyB = propertyB;
    }
  }

  @SerializerId2("test.extended_property_ordering")
  public static class OrderedPropertyBean2 extends AbstractOrderedPropertyBean {
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(Output.ALL)
    private SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("f")
    @DsonOutput(Output.ALL)
    private final String propertyF;

    @JsonProperty("g")
    @DsonOutput(Output.ALL)
    private String propertyG;

    @JsonCreator
    public OrderedPropertyBean2(
        @JsonProperty("b") String propertyB,
        @JsonProperty("c") String propertyC,
        @JsonProperty("a") String propertyA,
        @JsonProperty("f") String propertyF) {
      super(propertyA, propertyB, propertyC);
      this.propertyF = propertyF;
    }

    public OrderedPropertyBean2 propertyG(String propertyG) {
      this.propertyG = propertyG;
      return this;
    }
  }
}
