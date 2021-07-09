/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.constraintmachine.exceptions;

import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.ReducerState;

import java.util.List;

public final class ConstraintMachineException extends Exception {
	public ConstraintMachineException(int instIndex, List<REInstruction> instructions, ReducerState reducerState, Throwable cause) {
		super("index=" + instIndex + " reducerState=" + reducerState + "\n" + toMessage(instIndex, instructions), cause);
	}

	private static String toMessage(int instIndex, List<REInstruction> instructions) {
		var builder = new StringBuilder();

		for (int i = 0; i < instructions.size(); i++) {
			if (i == instIndex) {
				builder.append("<<<<Issue here>>>> ");
			}
			builder.append(i);
			builder.append(": ");
			builder.append(instructions.get(i));
			builder.append("\n");
		}

		return builder.toString();
	}
}
