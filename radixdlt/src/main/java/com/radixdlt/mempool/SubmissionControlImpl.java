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

package com.radixdlt.mempool;

import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToClientAtomConverter;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

public class SubmissionControlImpl implements SubmissionControl {
	private static final Logger log = LogManager.getLogger();

	public interface SubmissionControlSender {
		void sendDeserializeFailure(Atom rawAtom, AtomConversionException e);
		void sendRadixEngineFailure(ClientAtom clientAtom, RadixEngineException e);
	}

	private final Mempool mempool;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final Serialization serialization;
	private final AtomToClientAtomConverter converter;
	private final SubmissionControlSender submissionControlSender;

	public SubmissionControlImpl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		AtomToClientAtomConverter converter,
		SubmissionControlSender submissionControlSender
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);
		this.submissionControlSender = Objects.requireNonNull(submissionControlSender);
		this.converter = Objects.requireNonNull(converter);
	}

	@Override
	public void submitAtom(ClientAtom atom) throws MempoolFullException, MempoolDuplicateException {
		try {
			this.radixEngine.staticCheck(atom);
			this.mempool.addAtom(atom);
		} catch (RadixEngineException e) {
			log.info(
				"Rejecting atom {} with error '{}' at '{}' with message '{}'.",
				atom.getAID(),
				e.getErrorCode(),
				e.getDataPointer(),
				e.getMessage()
			);
			this.submissionControlSender.sendRadixEngineFailure(atom, e);
		}
	}

	@Override
	public void submitAtom(JSONObject atomJson, Consumer<ClientAtom> deserialisationCallback) throws MempoolFullException, MempoolDuplicateException {
		final Atom rawAtom = this.serialization.fromJsonObject(atomJson, Atom.class);
		try {
			final ClientAtom atom = converter.convert(rawAtom);
			deserialisationCallback.accept(atom);
			submitAtom(atom);
		} catch (AtomConversionException e) {
			log.info(
				"Rejecting atom {} due to conversion issues.",
				rawAtom.getAID()
			);
			this.submissionControlSender.sendDeserializeFailure(rawAtom, e);
		}
	}

	@Override
	public String toString() {
		return String.format("%s[%x]", getClass().getSimpleName(), System.identityHashCode(this));
	}
}
