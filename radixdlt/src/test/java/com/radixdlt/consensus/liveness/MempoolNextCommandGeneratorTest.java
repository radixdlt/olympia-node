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

package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import java.util.Collections;
import org.junit.Test;

public class MempoolNextCommandGeneratorTest {
	@Test
	public void when_generate_proposal_with_empty_prepared__then_generate_proposal_should_return_atom() {
		Mempool mempool = mock(Mempool.class);
		ClientAtom reAtom = mock(ClientAtom.class);
		when(mempool.getAtoms(anyInt(), anySet())).thenReturn(Collections.singletonList(reAtom));

		MempoolNextCommandGenerator proposalGenerator = new MempoolNextCommandGenerator(mempool);
		ClientAtom atom = proposalGenerator.generateNextCommand(View.of(1), Collections.emptySet());
		assertThat(atom).isEqualTo(reAtom);
	}
}