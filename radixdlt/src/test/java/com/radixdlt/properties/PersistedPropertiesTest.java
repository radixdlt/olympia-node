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
import static org.hamcrest.Matchers.*;

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
		assertThat(this.properties.get("a"), is("a"));
		assertThat(this.properties.get("b"), is("b"));
		assertThat(this.properties.get("c"), nullValue());
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
		assertThat(this.properties.get("a"), is("a"));
		assertThat(this.properties.get("b"), is("b"));
		assertThat(this.properties.get("c"), is("c"));
	}

	@Test(expected = ParseException.class)
	public void testLoadNoDefaultThrowsException() throws ParseException, IOException {
		final String testProperties = "notexist.properties";
		Files.deleteIfExists(Paths.get(testProperties));
		assertFalse(Files.exists(Paths.get(testProperties)));

		this.properties.load(testProperties); // Should throw exception
		fail();
	}

	@Test
	public void testGetInt() {
		populateData();
		assertEquals(12, this.properties.get("int.exist", -1));
		assertEquals(-1, this.properties.get("int.notexist", -1));
	}

	@Test
	public void testGetLong() {
		populateData();
		assertEquals(12L, this.properties.get("long.exist", -1L));
		assertEquals(-1L, this.properties.get("int.notexist", -1L));
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
		String result = this.properties.toString();
		assertThat(result, containsString("int.exist=12"));
		assertThat(result, containsString("long.exist=12"));
		assertThat(result, containsString("bool.true=true"));
		assertThat(result, containsString("bool.true1=1"));
	}

	private void populateData() {
		this.properties.set("int.exist", "12");
		this.properties.set("long.exist", "12");
		this.properties.set("bool.true", "true");
		this.properties.set("bool.false", "false");
		this.properties.set("bool.true1", "1");
		this.properties.set("bool.false0", "0");
		this.properties.set("string.exist", "string");
	}
}
