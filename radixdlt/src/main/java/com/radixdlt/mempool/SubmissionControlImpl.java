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

import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
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
	private final SubmissionControlSender submissionControlSender;
	private final Hasher hasher;

	public SubmissionControlImpl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		SubmissionControlSender submissionControlSender,
		Hasher hasher
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);
		this.submissionControlSender = Objects.requireNonNull(submissionControlSender);
		this.hasher = hasher;
	}

	@Override
	public void submitCommand(Command command) throws MempoolRejectedException, MempoolFullException, MempoolDuplicateException {
		ClientAtom clientAtom = command.map(payload -> {
			try {
				return serialization.fromDson(payload, ClientAtom.class);
			} catch (DeserializeException e) {
				return null;
			}
		});
		if (clientAtom == null) {
			//TODO: use of base class looks inconsistent (all other cases have dedicated exceptions)
			//TODO: create dedicated MempoolBadAtomException?
			throw new MempoolRejectedException(command, "Bad atom");
		}
		submitAtom(clientAtom);
	}

	@Override
	public void submitAtom(ClientAtom atom) throws MempoolFullException, MempoolDuplicateException {
		try {
			this.radixEngine.staticCheck(atom);
			byte[] payload = serialization.toDson(atom, Output.ALL);
			Command command = new Command(payload);
			this.mempool.add(command);
		} catch (RadixEngineException e) {
			log.info(
				"Rejecting atom {} with error '{}' at '{}' with message '{}'.",
				atom,
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
		final ClientAtom atom = ClientAtom.convertFromApiAtom(rawAtom, hasher);
		deserialisationCallback.accept(atom);
		submitAtom(atom);
	}

	@Override
	public String toString() {
		return String.format("%s[%x]", getClass().getSimpleName(), System.identityHashCode(this));
	}
}
