/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.HighQC;

/**
 * Reduces state for a pacemaker given some events
 * TODO: This is currently hack, should move to a more message based interface
 */
public interface PacemakerReducer {
	/**
	 * Signifies to the pacemaker that a quorum has agreed that a view has
	 * been completed.
	 *
	 * @param highQC the sync info for the view
	 */
	void processQC(HighQC highQC);
}
