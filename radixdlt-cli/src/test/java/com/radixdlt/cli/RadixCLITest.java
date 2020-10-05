package com.radixdlt.cli;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class RadixCLITest {

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
			assertTrue(output.startsWith("Error: Missing required argument(s): (-k=KEYSTORE -p=PASSWORD -n=KEYPAIR_NAME)"));
		}
	}

	@Test
	public void validatorKeyIsGenerated() {
		//Make sure file is not present
		final File keyStoreFile = new File("keystore.ks");
		keyStoreFile.delete();

		try (OutputCapture capture = OutputCapture.startStdout()) {
			RadixCLI.main(new String[] {"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});

			final String output = capture.stop().replace("\n", " ").trim();
			System.out.println("[" + output + "]");
			assertTrue(output.startsWith("Writing key node (pubKey: "));
			assertTrue(output.endsWith("to new keystore keystore.ks Done"));
		} finally {
			keyStoreFile.delete();
		}
	}
}