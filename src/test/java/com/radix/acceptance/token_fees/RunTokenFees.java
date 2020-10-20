package com.radix.acceptance.token_fees;
import org.junit.runner.RunWith;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.ECIESException;

import cucumber.api.CucumberOptions;
import cucumber.api.SnippetType;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, strict = true, monochrome = true, plugin = { "pretty" })
public class RunTokenFees {
	// Stub for running cucumber tests

	public static void main(String[] args) throws ECIESException {
		ECKeyPair kp = ECKeyPair.generateNew();

		byte[] toEncrypt = "Test message".getBytes();

		byte[] encrypted = kp.encrypt(toEncrypt);

		byte[] decrypted = kp.decrypt(encrypted);

		System.out.println(new String(decrypted));
	}
}
