package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The results of Constraint Machine validation
 */
public final class CMResult {
	private final ImmutableSet<CMError> cmErrors;
	private final CMAtom cmAtom;

	CMResult(ImmutableSet<CMError> cmErrors, CMAtom cmAtom) {
		this.cmErrors = Objects.requireNonNull(cmErrors);
		this.cmAtom = Objects.requireNonNull(cmAtom);
	}

	public <T extends Exception> CMAtom onSuccessElseThrow(Function<Set<CMError>, T> onError) throws T {
		if (cmErrors.isEmpty()) {
			return cmAtom;
		} else {
			throw onError.apply(cmErrors);
		}
	}

	public Set<CMError> getErrors() {
		return cmErrors;
	}
}
