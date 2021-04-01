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

import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.consensus.Command;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Pair;
import org.junit.Test;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.utils.RandomHasher;

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
	private final Observable<AtomsCommittedToLedger> ledgerCommitted = mock(Observable.class);
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher = mock(EventDispatcher.class);
	private final AtomIndex store = mock(AtomIndex.class);

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
		var atom = TxLowLevelBuilder.newBuilder()
			.message("Simple test message")
			.buildWithoutSignature();
		var jsonAtom = serialization.toJsonObject(atom, Output.API);

		var result = atomsService.submitAtom(jsonAtom);

		assertNotNull(result);
		verify(mempoolAddEventDispatcher).dispatch(any());
	}

	@Test
	public void atomCanBeRetrieved() {
		var atomAndId = createCommand();
		var optionalAtom = Optional.of(atomAndId.getFirst());
		var aid = atomAndId.getSecond();

		when(store.get(aid)).thenReturn(optionalAtom);

		var result = atomsService.getAtomByAtomId(aid);

		assertFalse(result.isEmpty());

		result.ifPresentOrElse(
			jsonAtom -> {
				assertEquals(":str:Test message", jsonAtom.getString("m"));
				assertEquals("radix.atom", jsonAtom.getString("serializer"));
			},
			() -> fail("Expecting non-empty result")
		);
	}

	private Pair<Atom, AID> createCommand() {
		var address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		var particle = new UniqueParticle("particle message", address);

		var atom = TxLowLevelBuilder.newBuilder()
			.up(particle)
			.particleGroup()
			.message("Test message")
			.buildWithoutSignature();

		var dson = DefaultSerialization.getInstance().toDson(atom, Output.ALL);
		return Pair.of(atom, new Command(dson).getId());
	}
}
