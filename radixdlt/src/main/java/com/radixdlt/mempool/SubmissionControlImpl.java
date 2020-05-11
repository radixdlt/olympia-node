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

import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToRadixEngineAtomConverter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

class SubmissionControlImpl implements SubmissionControl {
	private static final Logger log = LogManager.getLogger("submission");

	private final Mempool mempool;
	private final RadixEngine<SimpleRadixEngineAtom> radixEngine;
	private final Serialization serialization;
	private final Events events;
	private final AtomToRadixEngineAtomConverter converter;

	@Inject
	SubmissionControlImpl(
		Mempool mempool,
		RadixEngine<SimpleRadixEngineAtom> radixEngine,
		Serialization serialization,
		Events events,
		AtomToRadixEngineAtomConverter converter
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);
		this.events = Objects.requireNonNull(events);
		this.converter = Objects.requireNonNull(converter);
	}

	@Override
	public void submitAtom(Atom atom) throws MempoolFullException, MempoolDuplicateException {
		final SimpleRadixEngineAtom reAtom;
		try {
			reAtom = converter.convert(atom);
		} catch (AtomConversionException e) {
			log.info(
				"Rejecting atom {} due to conversion issues.",
				atom.getAID()
			);
			this.events.broadcast(new AtomExceptionEvent(e, atom.getAID()));
			return;
		}

		Optional<CMError> validationError = this.radixEngine.staticCheck(reAtom);

		if (validationError.isPresent()) {
			CMError error = validationError.get();
			ConstraintMachineValidationException ex = new ConstraintMachineValidationException(atom, error.getErrMsg(), error.getDataPointer());
			log.info(
				"Rejecting atom {} with constraint machine error '{}' at '{}'.",
				atom.getAID(),
				error.getErrorDescription(),
				error.getDataPointer()
			);
			this.events.broadcast(new AtomExceptionEvent(ex, atom.getAID()));
		} else {
			this.mempool.addAtom(reAtom);
		}
	}

	@Override
	public AID submitAtom(JSONObject atomJson, Consumer<Atom> deserialisationCallback)
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = this.serialization.fromJsonObject(atomJson, Atom.class);
		deserialisationCallback.accept(atom);
		submitAtom(atom);
		return atom.getAID();
	}

	@Override
	public String toString() {
		return String.format("%s[%x]", getClass().getSimpleName(), System.identityHashCode(this));
	}
}
