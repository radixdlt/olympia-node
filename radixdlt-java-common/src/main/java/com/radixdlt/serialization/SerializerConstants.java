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

package com.radixdlt.serialization;

/**
 * Package-local constants.
 */
public final class SerializerConstants {
	private SerializerConstants() {
		throw new IllegalStateException("Can't construct");
	}

	// At least this will cause compilation fail when updated
	public static final Class<SerializerId2> SERIALIZER_ID_ANNOTATION = SerializerId2.class;

	// At least this will cause compilation fail when updated
	public static final Class<SerializerRoot> SERIALIZER_ROOT_ANNOTATION = SerializerRoot.class;

	// The serialized type field name
	public static final String SERIALIZER_NAME = "sz";
}
