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

package org.radix.atoms.events;

import com.radixdlt.atommodel.Atom;
import org.radix.common.Syncronicity;
import org.radix.events.Event;

public abstract class AtomEvent extends Event
{
	private final Atom atom;

	public AtomEvent(Atom atom)
	{
		super();

		this.atom = atom;
	}

	public Atom getAtom()
	{
		return atom;
	}

	@Override
	public final boolean supportedSyncronicity(Syncronicity syncronicity)
	{
		if (syncronicity.equals(Syncronicity.ASYNCRONOUS) == false)
			return false;

		return true;
	}
}
