package com.radixdlt.constraintmachine;

import java.util.Objects;

public final class TransitionId {
	private final Class<? extends Particle> inputClass;
	private final Class<?> inputUsedClass;
	private final Class<? extends Particle> outputClass;
	private final Class<?> outputUsedClass;

	public TransitionId(
		Class<? extends Particle> inputClass,
		Class<?> inputUsedClass,
		Class<? extends Particle> outputClass,
		Class<?> outputUsedClass
	) {
		this.inputClass = inputClass;
		this.inputUsedClass = inputUsedClass;
		this.outputClass = outputClass;
		this.outputUsedClass = outputUsedClass;
	}

	@Override
	public int hashCode() {
		return Objects.hash(inputClass, inputUsedClass, outputClass, outputUsedClass);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TransitionId)) {
			return false;
		}

		TransitionId t = (TransitionId) obj;

		return t.inputClass == this.inputClass
			&& t.inputUsedClass == this.inputUsedClass
			&& t.outputClass == this.outputClass
			&& t.outputUsedClass == this.outputUsedClass;
	}
}
