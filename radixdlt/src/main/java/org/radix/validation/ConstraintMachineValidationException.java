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

package org.radix.validation;

import com.radixdlt.atommodel.Atom;

import java.util.Objects;
import com.radixdlt.constraintmachine.DataPointer;
import org.radix.exceptions.ValidationException;

/**
 * An exception during validation of an {@link Atom}
 */
public class ConstraintMachineValidationException extends ValidationException {
	private final Atom atom;
	private final DataPointer dataPointer;

	public ConstraintMachineValidationException(Atom atom, String message, DataPointer dataPointer) {
		super(message);

		Objects.requireNonNull(atom);
		Objects.requireNonNull(dataPointer);

		this.atom = atom;
		this.dataPointer = dataPointer;
	}

	public String getPointerToIssue() {
		return dataPointer.toString();
	}

	public Atom getAtom() {
		return atom;
	}
}