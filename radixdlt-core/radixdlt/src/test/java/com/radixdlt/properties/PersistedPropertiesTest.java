/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.cli.ParseException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PersistedPropertiesTest {

	private PersistedProperties properties;

	@Before
	public void setUp() {
		this.properties = new PersistedProperties();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PersistedProperties.class)
			.usingGetClass()
			.verify();
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
