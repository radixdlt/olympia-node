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

package com.radixdlt.constraintmachine;

import com.radixdlt.utils.Longs;

import java.util.Arrays;
import java.util.Iterator;

public final class ShutdownAll<D extends Particle> {
	private ShutdownAllIndex index;
	private final Iterator<D> iterator;

	public ShutdownAll(ShutdownAllIndex index, Iterator<D> iterator) {
		this.index = index;
		this.iterator = iterator;
	}

	public void verifyPostTypePrefixEquals(long l) throws ProcedureException {
		if (index.getPrefix().length != Long.BYTES + 1) {
			throw new ProcedureException("Invalid shutdownAll prefix");
		}
		var d = Longs.fromByteArray(index.getPrefix(), 1);
		if (d != l) {
			throw new ProcedureException("Invalid shutdownAll prefix");
		}
	}

	public void verifyPostTypePrefixIsEmpty() throws ProcedureException {
		if (index.getPrefix().length != 1) {
			throw new ProcedureException("Invalid shutdownAll prefix");
		}
	}

	public Iterator<D> iterator() {
		return iterator;
	}
}
