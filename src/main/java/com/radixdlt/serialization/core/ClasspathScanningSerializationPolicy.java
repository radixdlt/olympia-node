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

package com.radixdlt.serialization.core;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.serialization.ClassScanningSerializationPolicy;
import com.radixdlt.serialization.SerializationPolicy;
import com.radixdlt.serialization.SerializerConstants;
import java.util.Set;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

/**
 * Class that maintains a map of {@link DsonOutput.Output} types to
 * a set of pairs of classes and field/method names to output for that
 * serialization type.
 * <p>
 * This implementation works by scanning the classpath for classes
 * annotated with {@code SerializerConstants.SERIALIZER_ID_ANNOTATION} and
 * passing these classes to {@link ClassScanningSerializationPolicy}.
 */
public final class ClasspathScanningSerializationPolicy extends ClassScanningSerializationPolicy {

	/**
	 * Note that this class is expensive to create, potentially in the
	 * order of several seconds for a large classpath.
	 *
	 * @return A freshly created {@link ClasspathScanningSerializationPolicy}
	 */
	public static SerializationPolicy create() {
		return new ClasspathScanningSerializationPolicy();
	}


	@VisibleForTesting
	ClasspathScanningSerializationPolicy() {
		super(scanForSerializable());
	}

	private static Set<Class<?>> scanForSerializable() {
		Reflections reflections = getReflections("org.radix", "com.radixdlt");
		return reflections.getTypesAnnotatedWith(SerializerConstants.SERIALIZER_ID_ANNOTATION);
    }

	private static Reflections getReflections(String... packageNames) {
		ConfigurationBuilder config = new ConfigurationBuilder()
			.setUrls(ClasspathHelper.forJavaClassPath())
			.filterInputsBy(new FilterBuilder().includePackage(packageNames));
		return new Reflections(config);
	}
}
