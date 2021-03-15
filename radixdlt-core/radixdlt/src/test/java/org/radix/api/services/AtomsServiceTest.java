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
package org.radix.api.services;

import com.radixdlt.identifiers.AID;
import org.junit.Test;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.utils.RandomHasher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.reactivex.rxjava3.core.Observable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.radixdlt.serialization.DsonOutput.Output;

public class AtomsServiceTest {
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final Hasher hasher = new RandomHasher();
	private final Observable<AtomsRemovedFromMempool> mempoolAtomsRemoved = mock(Observable.class);
	private final Observable<MempoolAddFailure> mempoolAddFailures = mock(Observable.class);
	private final Observable<AtomCommittedToLedger> ledgerCommitted = mock(Observable.class);
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher = mock(EventDispatcher.class);
	private final LedgerEntryStore store = mock(LedgerEntryStore.class);

	private final AtomsService atomsService = new AtomsService(
		mempoolAtomsRemoved,
		mempoolAddFailures,
		ledgerCommitted,
		store,
		mempoolAddEventDispatcher,
		hasher,
		serialization
	);

	@Test
	public void atomCanBeSubmitted() {
		var atom = new Atom("Simple test message").buildAtom();
		var jsonAtom = serialization.toJsonObject(atom, Output.API);

		var result = atomsService.submitAtom(jsonAtom);

		assertNotNull(result);
		verify(mempoolAddEventDispatcher).dispatch(any());
	}

	@Test
	public void atomCanBeRetrieved() {
		var atom = createAtom();
		var optionalClientAtom = Optional.of(atom);
		var aid = atom.getAID();

		when(store.get(aid)).thenReturn(optionalClientAtom);

		var result = atomsService.getAtomByAtomId(aid);

		assertFalse(result.isEmpty());

		result.ifPresentOrElse(
			jsonAtom -> {
				assertEquals(":str:Test message", jsonAtom.getString("message"));
				assertEquals("radix.atom", jsonAtom.getString("serializer"));
			},
			() -> fail("Expecting non-empty result")
		);
	}

	private ClientAtom createAtom() {
		var address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		var particle = new UniqueParticle("particle message", address, 0);
		var group1 = ParticleGroup.of(SpunParticle.up(particle));

		return new Atom(List.of(group1), Map.of(), "Test message").buildAtom();
	}
}
