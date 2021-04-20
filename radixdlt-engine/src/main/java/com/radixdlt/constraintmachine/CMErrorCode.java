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

/**
 * Error codes from Constraint Machine validation.
 * TODO: add numeric codes
 */
public enum CMErrorCode {
	INCORRECT_SIGNATURE("Incorrect signature"),
	TOO_MANY_REQUIRED_SIGNATURES("Too many required signatures"),
	TOO_MANY_MESSAGES("Too many messages"),
	DATA_TOO_LARGE("Data is too large"),
	HOOK_ERROR("Hook error"),
	EMPTY_PARTICLE_GROUP("Empty group"),
	MISSING_PARTICLE_GROUP("Missing particle group"),
	UNKNOWN_OP("Unknown op"),
	SUBSTATE_NOT_FOUND("Substate not found"),
	READ_FAILURE("Substate read fail (does not exist)"),
	LOCAL_NONEXISTENT("Local non-existent"),
	INVALID_PARTICLE("Invalid particle"),
	PARTICLE_REGISTER_SPIN_CLASH("Particle spin clashes with current particle in register"),
	MISSING_TRANSITION_PROCEDURE("Transition procedure missing"),
	UNEQUAL_INPUT_OUTPUT("Inputs and outputs do not match"),
	NO_FULL_POP_ERROR("Neither input nor output are fully popped"),
	TRANSITION_PRECONDITION_FAILURE("Transition Precondition failure"),
	TRANSITION_ERROR("Transition error"),
	ARITHMETIC_ERROR("Arithmetic error"),
	INVALID_EXECUTION_PERMISSION("Invalid execution permission"),
	INVALID_INSTRUCTION_SEQUENCE("Invalid instruction sequence");

	private final String description;

	CMErrorCode(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
