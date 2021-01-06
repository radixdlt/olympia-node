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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.radixdlt.api.DeserializationFailure;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.api.SubmissionFailure;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.utils.TwoSenderToRx;
import io.reactivex.rxjava3.core.Observable;

/**
 * Module which manages messages from Syncer
 */
public final class LedgerRxModule extends AbstractModule {

	@Override
	protected void configure() {
		TwoSenderToRx<Atom, AtomConversionException, DeserializationFailure> deserializationFailures
			= new TwoSenderToRx<>(DeserializationFailure::new);
		TwoSenderToRx<ClientAtom, RadixEngineException, SubmissionFailure> submissionFailures
			= new TwoSenderToRx<>(SubmissionFailure::new);
		SubmissionControlSender submissionControlSender = new SubmissionControlSender() {
			@Override
			public void sendDeserializeFailure(Atom rawAtom, AtomConversionException e) {
				deserializationFailures.send(rawAtom, e);
			}

			@Override
			public void sendRadixEngineFailure(ClientAtom clientAtom, RadixEngineException e) {
				submissionFailures.send(clientAtom, e);
			}
		};
		SubmissionErrorsRx submissionErrorsRx = new SubmissionErrorsRx() {
			@Override
			public Observable<SubmissionFailure> submissionFailures() {
				return submissionFailures.rx();
			}

			@Override
			public Observable<DeserializationFailure> deserializationFailures() {
				return deserializationFailures.rx();
			}
		};
		bind(SubmissionControlSender.class).toInstance(submissionControlSender);
		bind(SubmissionErrorsRx.class).toInstance(submissionErrorsRx);
	}
}
