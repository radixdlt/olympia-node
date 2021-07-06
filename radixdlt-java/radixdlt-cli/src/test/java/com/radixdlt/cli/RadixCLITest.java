package com.radixdlt.cli;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RadixCLITest {
	private static final String KEYSTORE_FILENAME = "keystore.ks";

	@Before
	@After
	public void cleanupKeystore() {
		new File(KEYSTORE_FILENAME).delete();
	}

	@Test
	public void helpIsDisplayedIfInvokedWithoutParameters() {
		try (var capture = OutputCapture.startStdout()) {
			RadixCLI.execute(new String[0]);

			var output = capture.stop().trim();
			// Make sure existing commands are listed
			assertTrue(output.contains("generate-key"));
			assertTrue(output.contains("generate-validator-key"));
		}
	}

	@Test
	public void versionIsDisplayedWhenRequested() {
		try (var capture = OutputCapture.startStdout()) {
			RadixCLI.execute(new String[]{"-V"});

			assertEquals("1.0", capture.stop().trim());
		}
	}

	@Test
	public void generateValidatorKeyShowsHelpIfNotEnoughParameters() {
		try (var capture = OutputCapture.startStderr()) {
			RadixCLI.execute(new String[]{"generate-validator-key"});

			var output = capture.stop();
			assertTrue(output.startsWith("Error: Missing required argument(s): (-k=KEYSTORE -p=PASSWORD [-n=KEYPAIR_NAME])"));
		}
	}

	@Test
	public void validatorKeyIsGeneratedForNewKeystore() {
		try (var capture = OutputCapture.startStdout()) {
			RadixCLI.execute(new String[]{"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			var output = capture.stop().replace("\n", " ").trim();

			assertTrue(output.startsWith("Writing key node (pubKey: "));
			assertTrue(output.endsWith("to new keystore keystore.ks Done"));
		}
	}

	@Test
	public void validatorKeyIsGeneratedForExistingKeystore() {
		try (var ignore = OutputCapture.startStdout()) {
			RadixCLI.execute(new String[]{"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			ignore.stop();
		}

		try (var capture = OutputCapture.startStdout()) {
			RadixCLI.execute(new String[]{"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			var output = capture.stop().replace("\n", " ").trim();

			assertTrue(output.startsWith("Writing key node (pubKey: "));
			assertTrue(output.endsWith("to existing keystore keystore.ks Done"));
		}
	}
}
