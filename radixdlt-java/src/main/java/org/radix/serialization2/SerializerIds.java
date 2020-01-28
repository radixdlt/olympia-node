/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.radix.serialization2;

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