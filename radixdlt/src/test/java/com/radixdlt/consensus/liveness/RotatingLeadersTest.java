/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.View;
import org.junit.Test;

public class RotatingLeadersTest {
	@Test
	public void when_getting_leader_for_view_greater_than_size__leaders_are_round_robined() {
		ImmutableList<EUID> leaders = ImmutableList.of(new EUID(0), new EUID(1));
		RotatingLeaders rotatingLeaders = new RotatingLeaders(leaders);
		assertThat(rotatingLeaders.getProposer(View.of(leaders.size())))
			.isEqualTo(leaders.get(0));
		assertThat(rotatingLeaders.isValidProposer(leaders.get(0), View.of(leaders.size())))
			.isTrue();
		assertThat(rotatingLeaders.isValidProposer(leaders.get(1), View.of(leaders.size())))
			.isFalse();
	}
}