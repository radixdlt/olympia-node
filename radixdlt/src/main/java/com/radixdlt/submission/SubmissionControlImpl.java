package com.radixdlt.submission;

import java.util.Objects;

import javax.inject.Inject;

import com.radixdlt.common.Atom;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;

class SubmissionControlImpl implements SubmissionControl {
	private final Mempool mempool;

	@Inject
	SubmissionControlImpl(Mempool mempool) {
		this.mempool = Objects.requireNonNull(mempool);
	}

	@Override
	public void submitAtom(Atom atom) throws MempoolFullException, MempoolDuplicateException {
		// FIXME: Should perform at least static checks on atom via CM/RE before providing to mempool
		this.mempool.addAtom(atom);
	}
}
