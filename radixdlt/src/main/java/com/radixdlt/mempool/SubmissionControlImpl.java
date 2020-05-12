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
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToLedgerAtomConverter;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;

import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

class SubmissionControlImpl implements SubmissionControl {
	private static final Logger log = LogManager.getLogger("submission");

	private final Mempool mempool;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final Serialization serialization;
	private final Events events;
	private final AtomToLedgerAtomConverter converter;

	@Inject
	SubmissionControlImpl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		Events events,
		AtomToLedgerAtomConverter converter
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);
		this.events = Objects.requireNonNull(events);
		this.converter = Objects.requireNonNull(converter);
	}

	@Override
	public void submitAtom(LedgerAtom atom) throws MempoolFullException, MempoolDuplicateException {
		try {
			this.radixEngine.staticCheck(atom);
		} catch (RadixEngineException e) {
			log.info(
				"Rejecting atom {} with error '{}' at '{}'.",
				atom.getAID(),
				e.getErrorCode(),
				e.getDataPointer()
			);
			this.events.broadcast(new AtomExceptionEvent(e, atom.getAID()));
			return;
		}

		this.mempool.addAtom(atom);
	}

	@Override
	public AID submitAtom(JSONObject atomJson, Consumer<LedgerAtom> deserialisationCallback) throws MempoolFullException, MempoolDuplicateException {
		final Atom rawAtom = this.serialization.fromJsonObject(atomJson, Atom.class);
		final LedgerAtom atom;
		try {
			atom = converter.convert(rawAtom);
		} catch (AtomConversionException e) {
			log.info(
				"Rejecting atom {} due to conversion issues.",
				rawAtom.getAID()
			);
			this.events.broadcast(new AtomExceptionEvent(e, rawAtom.getAID()));
			return rawAtom.getAID();
		}

		deserialisationCallback.accept(atom);
		submitAtom(atom);
		return atom.getAID();
	}

	@Override
	public String toString() {
		return String.format("%s[%x]", getClass().getSimpleName(), System.identityHashCode(this));
	}
}
