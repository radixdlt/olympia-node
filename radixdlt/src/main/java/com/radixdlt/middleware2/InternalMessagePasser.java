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

package com.radixdlt.middleware2;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.api.DeserializationFailure;
import com.radixdlt.api.LedgerRx;
import com.radixdlt.api.StoredAtom;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.api.SubmissionFailure;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.syncer.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedRadixEngine.SyncedRadixEngineEventSender;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.api.StoredFailure;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.converters.AtomConversionException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * Acts as the "ether" to messages passed from sender to receiver
 */
public final class InternalMessagePasser implements
	CommittedStateSyncSender,
	CommittedStateSyncRx,
	EpochChangeSender,
	EpochChangeRx,
	SyncedRadixEngineEventSender,
	LedgerRx,
	SubmissionControlSender,
	SubmissionErrorsRx {
	private final Subject<CommittedStateSync> committedStateSyncsSubject = BehaviorSubject.<CommittedStateSync>create().toSerialized();
	private final Observable<CommittedStateSync> committedStateSyncs;
	private final Subject<EpochChange> epochChanges = BehaviorSubject.<EpochChange>create().toSerialized();
	private final Subject<StoredAtom> storedAtoms = BehaviorSubject.<StoredAtom>create().toSerialized();
	private final Subject<StoredFailure> storedExceptions = BehaviorSubject.<StoredFailure>create().toSerialized();
	private final Subject<SubmissionFailure> submissionFailures = BehaviorSubject.<SubmissionFailure>create().toSerialized();
	private final Subject<DeserializationFailure> deserializationFailures = BehaviorSubject.<DeserializationFailure>create().toSerialized();

	public InternalMessagePasser() {
		this.committedStateSyncs = committedStateSyncsSubject.publish().refCount();
	}

	@Override
	public Observable<CommittedStateSync> committedStateSyncs() {
		return committedStateSyncs;
	}

	@Override
	public void sendCommittedStateSync(long stateVersion, Object opaque) {
		committedStateSyncsSubject.onNext(new CommittedStateSync(stateVersion, opaque));
	}

	@Override
	public void epochChange(EpochChange epochChange) {
		epochChanges.onNext(epochChange);
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return epochChanges;
	}

	@Override
	public void sendStored(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
		storedAtoms.onNext(new StoredAtom(committedAtom, indicies));
	}

	@Override
	public Observable<StoredAtom> storedAtoms() {
		return storedAtoms;
	}

	@Override
	public void sendStoredFailure(CommittedAtom committedAtom, RadixEngineException e) {
		storedExceptions.onNext(new StoredFailure(committedAtom, e));
	}

	@Override
	public Observable<StoredFailure> storedExceptions() {
		return storedExceptions;
	}

	@Override
	public void sendDeserializeFailure(Atom rawAtom, AtomConversionException e) {
		deserializationFailures.onNext(new DeserializationFailure(rawAtom, e));
	}

	@Override
	public void sendRadixEngineFailure(ClientAtom clientAtom, RadixEngineException e) {
		submissionFailures.onNext(new SubmissionFailure(clientAtom, e));
	}

	@Override
	public Observable<SubmissionFailure> submissionFailures() {
		return submissionFailures;
	}

	@Override
	public Observable<DeserializationFailure> deserializationFailures() {
		return deserializationFailures;
	}
}
