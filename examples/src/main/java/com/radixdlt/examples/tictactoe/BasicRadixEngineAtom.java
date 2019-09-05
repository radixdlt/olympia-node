package com.radixdlt.examples.tictactoe;

import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngineAtom;

/**
 * Simple atom wrapper class for the instructions to run on the Constraint Machine
 */
final class BasicRadixEngineAtom implements RadixEngineAtom {
	private final CMInstruction instruction;
	private final String description;

	BasicRadixEngineAtom(CMInstruction instruction, String description) {
		this.instruction = instruction;
		this.description = description;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return instruction;
	}

	@Override
	public String toString() {
		return "[" + description + "]";
	}
}
