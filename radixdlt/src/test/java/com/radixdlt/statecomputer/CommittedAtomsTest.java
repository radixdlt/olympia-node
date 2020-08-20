/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import com.radixdlt.utils.TypedMocks;

import java.util.function.Consumer;
import org.junit.Test;

public class CommittedAtomsTest {
	@Test
	public void testSuccess() {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		CommittedAtomWithResult r = CommittedAtoms.success(committedAtom, ImmutableSet.of());
		assertThat(r.getCommittedAtom()).isEqualTo(committedAtom);
		Consumer<ImmutableSet<EUID>> successConsumer = TypedMocks.rmock(Consumer.class);
		Consumer<RadixEngineException> errorConsumer = TypedMocks.rmock(Consumer.class);
		r.ifSuccess(successConsumer);
		verify(successConsumer, times(1)).accept(any());
		r.ifError(errorConsumer);
		verify(errorConsumer, never()).accept(any());
	}

	@Test
	public void testError() {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		RadixEngineException e = mock(RadixEngineException.class);
		CommittedAtomWithResult r = CommittedAtoms.error(committedAtom, e);
		assertThat(r.getCommittedAtom()).isEqualTo(committedAtom);
		Consumer<ImmutableSet<EUID>> successConsumer = TypedMocks.rmock(Consumer.class);
		Consumer<RadixEngineException> errorConsumer = TypedMocks.rmock(Consumer.class);
		r.ifSuccess(successConsumer);
		verify(successConsumer, never()).accept(any());
		r.ifError(errorConsumer);
		verify(errorConsumer, times(1)).accept(eq(e));
	}
}