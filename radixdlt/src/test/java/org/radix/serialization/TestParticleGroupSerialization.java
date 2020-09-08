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

package org.radix.serialization;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.serialization.Serialization;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Strings;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.serialization.DsonOutput.Output;

import static org.junit.Assert.assertEquals;

public class TestParticleGroupSerialization {
	static Serialization serialization;

	@BeforeClass
	public static void setupSerializer() {
		serialization = DefaultSerialization.getInstance();
	}

	@Test
	public void testLargeStringSerialization() {
		// "massive" must be greater length than (16000 / 4) - 4 = 3996
		String massive = Strings.repeat("X", 4096);
		ParticleGroup pg = ParticleGroup.builder().addMetaData("massive", massive).build();

		byte[] particleGroupBytes = serialization.toDson(pg, Output.HASH);

		assertEquals(4162, particleGroupBytes.length);
	}
}
