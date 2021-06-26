package com.radixdlt.cloud;

import org.junit.Test;

public class AWSecretsTest {

	/*
			"--secret-password-key=${secretPasswordKey}"
RadixCLI.main(new String[] {"generate-validator-key", "-k=keystore.ks", "-n=node", "-p=nopass"});

	 */
	@Test
	public void call() {
		AWSSecrets.main(new String[]{"--enable-aws-secrets=false",
			"--recreate-aws-secrets=false",
			"--network-name=test_secrets",
			"--node-name-prefix=node",
			"--enable-aws-secrets=false",
			"--recreate-aws-secrets=false",
			"--node-number=0",
			"--node-names=test_secrets_ap_south_1_node0"});


	}
}
