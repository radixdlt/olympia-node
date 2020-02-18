package com.radixdlt.submission;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.json.JSONObject;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.serialization.Serialization;

class SubmissionControlImpl implements SubmissionControl {
	private final Mempool mempool;
	private final RadixEngine radixEngine;
	private final Serialization serialization;
	private final Events events;

	@Inject
	SubmissionControlImpl(Mempool mempool, RadixEngine radixEngine, Serialization serialization, Events events) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);
		this.events = Objects.requireNonNull(events);
	}

	@Override
	public void submitAtom(Atom atom) throws MempoolFullException, MempoolDuplicateException {
		Optional<CMError> validationError = this.radixEngine.staticCheck(atom);
		if (validationError.isPresent()) {
			CMError error = validationError.get();
			ConstraintMachineValidationException ex = new ConstraintMachineValidationException(atom, error.getErrMsg(), error.getDataPointer());
			this.events.broadcast(new AtomExceptionEvent(ex, atom.getAID()));
		} else {
			this.mempool.addAtom(atom);
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
}
