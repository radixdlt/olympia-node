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

package com.radixdlt.atomos;

import java.util.Objects;

import org.junit.Test;

import com.radixdlt.constraintmachine.Particle;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class CMAtomOSTest {
	private static final class TestParticle implements Particle {
		@Override
		public String toString() {
			return "Test";
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof TestParticle)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(0);
		}
	}

	@Test
	public void when_a_particle_which_is_not_registered_via_os_is_validated__it_should_cause_errors() {
		var os = new CMAtomOS();
		var staticCheck = os.buildStatelessSubstateVerifier();
		TestParticle testParticle = new TestParticle();
		assertThatThrownBy(() -> staticCheck.verify(testParticle))
			.hasMessageContaining("Unknown particle type");
	}
}