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

public final class TransitionToken<I extends Particle, O extends Particle, U extends UsedData> {
	private final Class<I> inputClass;
	private final Class<O> outputClass;
	private final TypeToken<U> usedClass;

	public TransitionToken(
		Class<I> inputClass,
		Class<O> outputClass,
		TypeToken<U> usedClass
	) {
		this.inputClass = Objects.requireNonNull(inputClass);
		this.usedClass = Objects.requireNonNull(usedClass);
		this.outputClass = Objects.requireNonNull(outputClass);
	}

	public Class<I> getInputClass() {
		return inputClass;
	}

	public Class<O> getOutputClass() {
		return outputClass;
	}

	public TypeToken<U> getUsedClass() {
		return usedClass;
	}

	@Override
	public String toString() {
		return inputClass.getSimpleName() + " " + outputClass.getSimpleName() + " " + usedClass.getRawType().getSimpleName();
	}

	@Override
	public int hashCode() {
		return Objects.hash(inputClass, outputClass, usedClass);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TransitionToken)) {
			return false;
		}

		TransitionToken<?, ?, ?> t = (TransitionToken<?, ?, ?>) obj;

		return Objects.equals(t.inputClass, this.inputClass)
			&& Objects.equals(t.outputClass, this.outputClass)
			&& Objects.equals(t.usedClass, this.usedClass);
	}
}
