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

package com.radixdlt.engine;

import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.PermissionLevel;

/**
 * This module checks for constraints outside of the FSM constraint
 * machine
 */
public interface PostParsedChecker {

	/**
	 * Checks that an atom is well-formed
	 * @return result of the check
	 */
	void check(PermissionLevel permissionLevel, REProcessedTxn radixEngineTxn) throws ConstraintMachineException;
}
