package com.radixdlt.cli;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RadixCLITest {
	private static final String KEYSTORE_FILENAME = "keystore.ks";

	@Before
	public void setUp() {
		cleanupKeystore();
	}

	@Test
	public void helpIsDisplayedIfInvokedWithoutParameters() {
		try (OutputCapture capture = OutputCapture.startStdout()) {
			RadixCLI.main(new String[0]);

			String output = capture.stop().trim();
			// Make sure existing commands are listed
			assertTrue(output.contains("generate-key"));
			assertTrue(output.contains("get-messages"));
			assertTrue(output.contains("send-message"));
			assertTrue(output.contains("get-details"));
			assertTrue(output.contains("get-stored-atoms"));
			assertTrue(output.contains("create-mint-token"));
			assertTrue(output.contains("register-validator"));
			assertTrue(output.contains("unregister-validator"));
			assertTrue(output.contains("show-validator-config"));
			assertTrue(output.contains("generate-validator-key"));
		}
	}

	@Test
	public void versionIsDisplayedWhenRequested() {
		try (OutputCapture capture = OutputCapture.startStdout()) {
			RadixCLI.main(new String[] {"-V"});

			assertEquals("1.0", capture.stop().trim());
		}
	}

	@Test
	public void generateValidatorKeyShowsHelpIfNotEnoughParameters() {
		try (OutputCapture capture = OutputCapture.startStderr()) {
			RadixCLI.main(new String[] {"generate-validator-key"});

			final String output = capture.stop();
			assertTrue(output.startsWith("Error: Missing required argument(s): (-k=KEYSTORE -p=PASSWORD [-n=KEYPAIR_NAME])"));
		}
	}

	@Test
	public void validatorKeyIsGeneratedForNewKeystore() {
		try (OutputCapture capture = OutputCapture.startStdout()) {
			RadixCLI.main(new String[] {"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			final String output = capture.stop().replace("\n", " ").trim();

			assertTrue(output.startsWith("Writing key node (pubKey: "));
			assertTrue(output.endsWith("to new keystore keystore.ks Done"));
		} finally {
			cleanupKeystore();
		}
	}

	@Test
	public void validatorKeyIsGeneratedForExistingKeystore() {
		try (OutputCapture ignore = OutputCapture.startStdout()) {
			RadixCLI.main(new String[] {"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			ignore.stop();
		}

		try (OutputCapture capture = OutputCapture.startStdout()) {
			RadixCLI.main(new String[] {"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});
			final String output = capture.stop().replace("\n", " ").trim();

			assertTrue(output.startsWith("Writing key node (pubKey: "));
			assertTrue(output.endsWith("to existing keystore keystore.ks Done"));
		} finally {
			cleanupKeystore();
		}
	}

	private void cleanupKeystore() {
		new File(KEYSTORE_FILENAME).delete();
	}
}