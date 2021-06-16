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

package org.radix;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateUniversesTest {

	@BeforeClass
	public static void firstRun() throws IOException {
		// Need to do a first run before tests to ensure that logging
		// from static initialisation is not included in other tests.
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-c", "-j", "-v", "1"));
			stdOut.toString();
			stdErr.toString();
		}
	}

	@Test
	public void testInvalidOpt() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-x"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).startsWith("Unrecognized option");
		assertThat(out).startsWith("usage: GenerateUniverses");
	}

	@Test
	public void testInvalidArg() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("x"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).startsWith("Extra arguments:");
		assertThat(out).startsWith("usage: GenerateUniverses");
	}

	@Test
	public void testHelpOpt() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-h"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).isEmpty();
		assertThat(out).startsWith("usage: GenerateUniverses");
	}

	@Test
	public void testNoDsonNoJson() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-c", "-j", "-v", "1"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).isEmpty();
		assertThat(out).isEmpty();
	}

	@Test
	public void testDsonWithPrivkey() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-j", "-p", "-t", "test", "-v", "1"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).isEmpty();
		assertThat(out)
			.hasLineCount(7)
			.containsSubsequence(
				"RADIXDLT_VALIDATOR_0_PRIVKEY=", "\n",
				"RADIXDLT_VALIDATOR_0_PUBKEY=", "\n",
				"RADIXDLT_STAKER_0_PRIVKEY=", "\n",
				"RADIXDLT_STAKER_0_PUBKEY=", "\n",
				"RADIXDLT_UNIVERSE_TYPE=TEST", "\n",
				"RADIXDLT_UNIVERSE_TOKEN=", "\n",
				"RADIXDLT_UNIVERSE=", "\n"
			);
	}

	@Test
	public void testDsonWithoutPrivkey() throws IOException {
		final String out;
		final String err;
		try (Capture stdOut = Capture.forOut();
			 Capture stdErr = Capture.forErr()) {
			GenerateUniverses.main(strings("-j", "-t", "test", "-v", "1"));
			out = stdOut.toString();
			err = stdErr.toString();
		}
		assertThat(err).isEmpty();
		assertThat(out)
			.hasLineCount(3)
			.containsSubsequence(
				"RADIXDLT_UNIVERSE_TYPE=TEST", "\n",
				"RADIXDLT_UNIVERSE_TOKEN=", "\n",
				"RADIXDLT_UNIVERSE=", "\n"
			);
	}

	// Largely to combat checkstyle whining about "whitespace after {"
	// when using 'new String[] { "foo", "bar" }'.
	private static String[] strings(String... strings) {
		return strings;
	}

	private static class Capture implements Closeable {
		private final PrintStream captureStream;
		private final ByteArrayOutputStream capturedData;
		private final PrintStream originalStream;
		private final Consumer<PrintStream> streamSetter;

		private Capture(PrintStream originalStream, Consumer<PrintStream> streamSetter) {
			this.capturedData = new ByteArrayOutputStream();
			this.captureStream = new PrintStream(this.capturedData);
			this.originalStream = originalStream;
			this.streamSetter = streamSetter;
			streamSetter.accept(this.captureStream);
		}

		static Capture forOut() {
			return new Capture(System.out, System::setOut);
		}

		static Capture forErr() {
			return new Capture(System.err, System::setErr);
		}

		@Override
		public String toString() {
			return new String(this.capturedData.toByteArray(), StandardCharsets.UTF_8);
		}

		@Override
		public void close() throws IOException {
			this.captureStream.close();
			this.capturedData.close();
			streamSetter.accept(this.originalStream);
		}
	}
}
