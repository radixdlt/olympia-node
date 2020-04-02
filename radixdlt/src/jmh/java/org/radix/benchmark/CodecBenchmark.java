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

package org.radix.benchmark;


import com.radixdlt.DefaultSerialization;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;
import org.radix.logging.Logging;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

import org.radix.serialization.DummyTestObject;
import org.radix.serialization.TestSetupUtils;

/**
 * Some JMH driven benchmarks for testing serialisation performance of
 * Radix and some third party libraries.
 * <p>
 * Note that the build system has been set up to make it easier to
 * run these performance tests under gradle.  Using gradle, it should
 * be possible to execute:
 * <pre>
 *    $ gradle clean jmh
 * </pre>
 * from the RadixCode/radixdlt directory.  Note that the JMH plugin
 * does not appear to be super robust, and changes to benchmark tests
 * and other code are not always re-instrumented correctly by gradle
 * daemons.  This can be worked around by stopping the daemons before
 * starting the tests using:
 * <pre>
 *    $ gradle --stop && gradle clean jmh
 * </pre>
 * Alternatively, the configuration option {@code org.gradle.daemon=false}
 * can be added to the {@code ~/.gradle/gradle.properties} to completely
 * disable gradle daemons.
 */
public class CodecBenchmark {

	private static final DummyTestObject testObject;

	private static Serialization serialization;

	private static String jacksonJson;
	private static byte[] jacksonBytes;

	static {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Logging.getLogger().setLevels(Logging.ALL & ~Logging.INFO & ~Logging.TRACE & ~Logging.DEBUG);
		try {
			TestSetupUtils.installBouncyCastleProvider();

			serialization = DefaultSerialization.getInstance();

			testObject = new DummyTestObject(true);

			jacksonJson = serialization.toJson(testObject, Output.ALL);
			jacksonBytes = serialization.toDson(testObject, Output.ALL);

			System.out.format("DSON bytes length: %s%n", jacksonBytes.length);
			System.out.format("JSON bytes length: %s%n", jacksonJson.length());
		} catch (SerializationException ex) {
			throw new IllegalStateException("Can't initialise test objects", ex);
		}
	}

	@Benchmark
	public void jacksonToBytesTest(Blackhole bh) {
		try {
			byte[] bytes = serialization.toDson(testObject, Output.WIRE);
			bh.consume(bytes);
		} catch (SerializationException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Benchmark
	public void jacksonFromBytesTest(Blackhole bh) {
		try {
			DummyTestObject newObj = serialization.fromDson(jacksonBytes, DummyTestObject.class);
			bh.consume(newObj);
		} catch (SerializationException ex) {
			throw new RuntimeException(ex);
		}
	}


	@Benchmark
	public void jacksonToJsonTest(Blackhole bh) {
		try {
			String json = serialization.toJson(testObject, Output.WIRE);
			bh.consume(json);
		} catch (SerializationException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Benchmark
	public void jacksonFromJsonTest(Blackhole bh) {
		try {
			DummyTestObject newObj = serialization.fromJson(jacksonJson, DummyTestObject.class);
			bh.consume(newObj);
		} catch (SerializationException ex) {
			throw new RuntimeException(ex);
		}
	}
}
