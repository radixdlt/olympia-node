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
	MISSING_PROCEDURE("Transition procedure missing"),
	PROCEDURE_ERROR("Procedure error"),
	PERMISSION_LEVEL_ERROR("Invalid execution permission"),
	AUTHORIZATION_ERROR("Authorization error"),
	INVALID_INSTRUCTION_SEQUENCE("Invalid instruction sequence"),
	UNKNOWN_ERROR("Unknown error");

	private final String description;

	CMErrorCode(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
