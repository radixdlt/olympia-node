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

package com.radixdlt.utils.properties;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.commons.cli.ParseException;
import org.junit.Before;
import org.junit.Test;

public class PersistedPropertiesTest {

  private PersistedProperties properties;

  @Before
  public void setUp() {
    this.properties = new PersistedProperties();
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(PersistedProperties.class).usingGetClass().verify();
  }

  @Test
  public void testLoadFromResources() throws ParseException, IOException {
    final String testProperties = "test.properties";
    Files.deleteIfExists(Paths.get(testProperties));
    assertFalse(Files.exists(Paths.get(testProperties)));

    this.properties.load(testProperties); // Should load from resources

    assertEquals("a", this.properties.get("a"));
    assertEquals("b", this.properties.get("b"));
    assertTrue(this.properties.get("c") == null);
    assertEquals("abc", this.properties.get("aaa"));
    assertEquals("\"       \"", this.properties.get("bbb"));
  }

  @Test
  public void testLoadFromDisk() throws ParseException, IOException {
    final String testProperties = "test.properties";
    Files.deleteIfExists(Paths.get(testProperties));
    assertFalse(Files.exists(Paths.get(testProperties)));

    this.properties.load(testProperties); // Should load from resources
    this.properties.set("c", "c");
    this.properties.save(testProperties);
    assertTrue(Files.exists(Paths.get(testProperties))); // Now have properties file on disk

    PersistedProperties newCut = new PersistedProperties();
    newCut.load(testProperties); // Should load from file

    assertEquals("a", this.properties.get("a"));
    assertEquals("b", this.properties.get("b"));
    assertEquals("c", this.properties.get("c"));
  }

  @Test(expected = ParseException.class)
  public void testLoadNoDefaultThrowsException() throws IOException, ParseException {
    final String testProperties = "notexist.properties";
    Files.deleteIfExists(Paths.get(testProperties));
    assertFalse(Files.exists(Paths.get(testProperties)));

    this.properties.load(testProperties);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetInt() {
    populateData();
    assertEquals(12, this.properties.get("int.exist", -1));
    assertEquals(-1, this.properties.get("int.notexist", -1));

    this.properties.get("string.exist", -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetLong() {
    populateData();
    assertEquals(12L, this.properties.get("long.exist", -1L));
    assertEquals(-1L, this.properties.get("int.notexist", -1L));

    this.properties.get("string.exist", -1L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetDouble() {
    populateData();
    assertEquals(1.2, this.properties.get("double.exist", -1.0), Math.ulp(1.2));
    assertEquals(-1.0, this.properties.get("double.notexist", -1.0), Math.ulp(1.0));

    this.properties.get("string.exist", -1.0);
  }

  @Test
  public void testGetBoolean() {
    populateData();
    assertTrue(this.properties.get("bool.true", false));
    assertTrue(this.properties.get("bool.true1", false));
    assertFalse(this.properties.get("bool.false", true));
    assertFalse(this.properties.get("bool.false0", true));
    assertTrue(this.properties.get("bool.notexist", true));
    assertFalse(this.properties.get("bool.notexist", false));
  }

  @Test
  public void testGetString() {
    populateData();
    assertEquals("string", this.properties.get("string.exist", "default"));
    assertEquals("default", this.properties.get("string.notexist", "default"));
  }

  @Test
  public void testToString() {
    populateData();

    var result = this.properties.toString();
    assertTrue(result.contains("int.exist"));
    assertTrue(result.contains("long.exist"));
    assertTrue(result.contains("bool.true"));
    assertTrue(result.contains("bool.true1"));
  }

  private void populateData() {
    this.properties.set("int.exist", "12");
    this.properties.set("long.exist", "12");
    this.properties.set("double.exist", "1.2");
    this.properties.set("bool.true", "true");
    this.properties.set("bool.false", "false");
    this.properties.set("bool.true1", "1");
    this.properties.set("bool.false0", "0");
    this.properties.set("string.exist", "string");
  }
}
