/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package org.radix;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.radix.Radix.calculateVersionString;

public class RadixVersionStringTest {
	@Test
	public void testCalculateVersionForCleanRepo() {
		var details = Map.<String, Object>of(
			"tag", "1.0-beta.35.1",
			"last_tag", "1.0-beta.35.1"
		);

		var version = calculateVersionString(details);

		assertEquals("1.0-beta.35.1", version);
	}

	@Test
	public void testCalculateVersionForDirtyRepo() {
		var details = Map.<String, Object>of(
			"tag", "",
			"last_tag", "1.0-beta.35.1",
			"build", "ed0717c",
			"branch", "feature/rpnv1-1306-refactor-json-rpc-implementation"
		);

		var version = calculateVersionString(details);

		assertEquals("1.0-beta.35.1-feature~rpnv1-1306-refactor-json-rpc-implementation-ed0717c", version);
	}

	@Test
	public void testCalculateVersionForDetachedHead() {
		var details = Map.<String, Object>of(
			"tag", "",
			"last_tag", "1.0-beta.35.1",
			"build", "ed0717c"
		);

		var version = calculateVersionString(details);

		assertEquals("detached-head-ed0717c", version);
	}

}