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
 * Interface for accessing serializer IDs given a class or vice-versa.
 */
public interface SerializerIds {

	/**
	 * Return the serializer ID, or {@code null} if no serializer known.
	 *
	 * @param cls The class to retrieve the serializer ID for.
	 * @return The serializer ID, or {@code null} if no serializer known.
	 */
	String getIdForClass(Class<?> cls);

	/**
	 * Return an object's class, given the ID.  If the serializer ID
	 * is unknown, {@code null} is returned.
	 *
	 * @param id The serializer ID to find the mapped class for.
	 * @return The class corresponding to the serializer ID, or {@code null}
	 *			if serializer ID unknown.
	 */
	Class<?> getClassForId(String id);

	/**
	 * Return true if class is serializable, or a supertype of
	 * a serializable class, excluding {@code Object}.
	 * @param cls The class to check
	 * @return {@code true} if class is serializable, or a supertype
	 */
	boolean isSerializableSuper(Class<?> cls);

}