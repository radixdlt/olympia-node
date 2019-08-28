package com.radixdlt.constraintmachine;

import com.google.common.reflect.TypeToken;
import java.util.Objects;

public final class TransitionToken {
	private final Class<? extends Particle> inputClass;
	private final TypeToken<? extends UsedData> inputUsedClass;
	private final Class<? extends Particle> outputClass;
	private final TypeToken<? extends UsedData> outputUsedClass;

	public TransitionToken(
		Class<? extends Particle> inputClass,
		TypeToken<? extends UsedData> inputUsedClass,
		Class<? extends Particle> outputClass,
		TypeToken<? extends UsedData> outputUsedClass
	) {
		this.inputClass = inputClass;
		this.inputUsedClass = inputUsedClass;
		this.outputClass = outputClass;
		this.outputUsedClass = outputUsedClass;
	}

	@Override
	public String toString() {
		return inputClass.getSimpleName() + " " + inputUsedClass + " " + outputClass.getSimpleName() + " " + outputUsedClass;
	}

	@Override
	public int hashCode() {
		return Objects.hash(inputClass, inputUsedClass, outputClass, outputUsedClass);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TransitionToken)) {
			return false;
		}

		TransitionToken t = (TransitionToken) obj;

		return Objects.equals(t.inputClass, this.inputClass)
			&& Objects.equals(t.inputUsedClass, this.inputUsedClass)
			&& Objects.equals(t.outputClass, this.outputClass)
			&& Objects.equals(t.outputUsedClass, this.outputUsedClass);
	}
}
