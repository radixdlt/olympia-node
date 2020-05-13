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

package com.radixdlt.middleware2.converters;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.middleware2.ClientAtom;

/**
 * Converts an api atom to an atom which can be run in a RadixEngine.
 */
public interface AtomToClientAtomConverter {
	ClientAtom convert(Atom atom) throws AtomConversionException;
}
