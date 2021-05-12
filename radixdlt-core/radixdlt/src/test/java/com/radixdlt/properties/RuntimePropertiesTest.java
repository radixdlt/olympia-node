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
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RuntimePropertiesTest {

	private static final String TEST_PROPERTIES = "test.properties";
	private static final String TEST_OPTION = "test option";

	private final JSONObject options = new JSONObject(
		  "{\n"
		+ "	\"config\":{\n"
		+ "		\"short\":\"c\",\n"
		+ "		\"desc\":\"The configuration to load\",\n"
		+ "		\"has_arg\":true\n"
		+ "	},\n"
		+ "	\"test_arg\":{\n"
		+ "		\"short\":\"ta\",\n"
		+ "		\"desc\":\"Test option with arg\",\n"
		+ "		\"has_arg\":true\n"
		+ "	},\n"
		+ "	\"test_noarg\":{\n"
		+ "		\"short\":\"tn\",\n"
		+ "		\"desc\":\"Test option with no arg\",\n"
		+ "		\"has_arg\":false\n"
		+ "	},\n"
		+ "}"
	);
	private RuntimeProperties properties;

	@Before
	public void setUp() throws ParseException, IOException {
		String[] cmdLine = new String[] {
			"-c", TEST_PROPERTIES,
			"-ta", TEST_OPTION,
			"-tn"
		};
		Files.deleteIfExists(Paths.get(TEST_PROPERTIES));
		assertFalse(Files.exists(Paths.get(TEST_PROPERTIES)));
		this.properties = new RuntimeProperties(options, cmdLine);
		Files.deleteIfExists(Paths.get(TEST_PROPERTIES));
		assertFalse(Files.exists(Paths.get(TEST_PROPERTIES)));
	}

	@After
	public void shutDown() throws IOException {
		Files.deleteIfExists(Paths.get(TEST_PROPERTIES));
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RuntimeProperties.class)
			.usingGetClass()
			.withNonnullFields("commandLine")
			.verify();
	}

	@Test
	public void testGet() {
		assertThat(this.properties.get("a")).isEqualTo("a");
		assertThat(this.properties.get("b")).isEqualTo("b");
		assertThat(this.properties.get("c")).isEqualTo(TEST_PROPERTIES);
		assertThat(this.properties.get("ta")).isEqualTo(TEST_OPTION);
		assertThat(this.properties.get("tn")).isEqualTo("1");
		assertThat(this.properties.get("notexist")).isNull();
	}

	@Test
	public void testToString() {
		var result = this.properties.toString();
		System.out.println(result);

		assertTrue(result.contains("[aaa, a, b, bbb]"));
		assertTrue(result.contains("args=[]"));
	}
}
