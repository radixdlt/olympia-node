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

package com.radixdlt.constraintmachine;

import com.google.common.reflect.TypeToken;
import java.util.Objects;

public final class TransitionToken<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {
	private final Class<I> inputClass;
	private final TypeToken<N> inputUsedClass;
	private final Class<O> outputClass;
	private final TypeToken<U> outputUsedClass;

	public TransitionToken(
		Class<I> inputClass,
		TypeToken<N> inputUsedClass,
		Class<O> outputClass,
		TypeToken<U> outputUsedClass
	) {
		if (!Objects.equals(inputUsedClass, TypeToken.of(VoidUsedData.class))
			&& !Objects.equals(outputUsedClass, TypeToken.of(VoidUsedData.class))) {
			throw new IllegalArgumentException("There must be atleast one VoidUsedData type.");
		}

		this.inputClass = Objects.requireNonNull(inputClass);
		this.inputUsedClass = Objects.requireNonNull(inputUsedClass);
		this.outputClass = Objects.requireNonNull(outputClass);
		this.outputUsedClass = Objects.requireNonNull(outputUsedClass);
	}

	public Class<I> getInputClass() {
		return inputClass;
	}

	public Class<O> getOutputClass() {
		return outputClass;
	}

	public TypeToken<N> getInputUsedClass() {
		return inputUsedClass;
	}

	public TypeToken<U> getOutputUsedClass() {
		return outputUsedClass;
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

		TransitionToken<?, ?, ?, ?> t = (TransitionToken<?, ?, ?, ?>) obj;

		return Objects.equals(t.inputClass, this.inputClass)
			&& Objects.equals(t.inputUsedClass, this.inputUsedClass)
			&& Objects.equals(t.outputClass, this.outputClass)
			&& Objects.equals(t.outputUsedClass, this.outputUsedClass);
	}
}
