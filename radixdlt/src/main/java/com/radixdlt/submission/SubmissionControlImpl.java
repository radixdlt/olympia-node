package com.radixdlt.submission;

import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;

class SubmissionControlImpl implements SubmissionControl {
	private final Mempool mempool;
	private final RadixEngine radixEngine;
	private final Events events;

	@Inject
	SubmissionControlImpl(Mempool mempool, RadixEngine radixEngine, Events events) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
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
}
