/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */
package com.radixdlt.client.lib.api.sync;

import org.junit.Test;

import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.networks.Addressing;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.radixdlt.client.lib.api.sync.SyncRadixApiTestUtils.prepareClient;
import static com.radixdlt.client.lib.api.token.Amount.amount;

public class SyncRadixApiValidatorTest {
	private static final String LIST = "{\"result\":{\"cursor\":\"dv1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay"
		+ "4ammw2cnumc3jtmxl\",\"validators\":[{\"totalDelegatedStake\":\"80130000000000000000000\",\"uptimePer"
		+ "centage\":\"100.00\",\"proposalsMissed\":0,\"address\":\"dv1qghsre0ptn9r28d07wzrldc08shs5x7aqhj6lzy2"
		+ "vauyaulppg4qztrktyj\",\"infoURL\":\"\",\"ownerDelegation\":\"80130000000000000000000\",\"name\":\"\""
		+ ",\"validatorFee\":\"0.0\",\"registered\":true,\"ownerAddress\":\"ddx1qspz7q09u9wv5dga4lecg0ahpu7z7zs"
		+ "mm5z7ttug3fnhsnhnuy9z5qgpe3507\",\"isExternalStakeAccepted\":true,\"proposalsCompleted\":9871},{\"to"
		+ "talDelegatedStake\":\"80100000000000000000000\",\"uptimePercentage\":\"100.00\",\"proposalsMissed\":"
		+ "0,\"address\":\"dv1qwkdfp8z7rrlv5cf45tc4864n277p9ukjax90ec5cd03zr0uylxtu7s9762\",\"infoURL\":\"\",\""
		+ "ownerDelegation\":\"80100000000000000000000\",\"name\":\"\",\"validatorFee\":\"0.0\",\"registered\":"
		+ "true,\"ownerAddress\":\"ddx1qsp6e4yyutcv0ajnpxk30z5l2kdtmcyhj6t5c4l8znp47ygdlsnue0sc37dv8\",\"isExte"
		+ "rnalStakeAccepted\":true,\"proposalsCompleted\":9867},{\"totalDelegatedStake\":\"8010000000000000000"
		+ "0000\",\"uptimePercentage\":\"100.00\",\"proposalsMissed\":0,\"address\":\"dv1q0llj774w40wafpqg5apgd"
		+ "2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\",\"infoURL\":\"\",\"ownerDelegation\":\"801000000000000000000"
		+ "00\",\"name\":\"\",\"validatorFee\":\"0.0\",\"registered\":true,\"ownerAddress\":\"ddx1qspll7tm6464a"
		+ "m4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sfvvdfj\",\"isExternalStakeAccepted\":true,\"proposalsCompl"
		+ "eted\":9867},{\"totalDelegatedStake\":\"80100000000000000000000\",\"uptimePercentage\":\"100.00\",\""
		+ "proposalsMissed\":0,\"address\":\"dv1qwsyxnv7gleusc34ga78kxhx4ewngsk5nvv58s4h22ngu2j8ufruwzer947\","
		+ "\"infoURL\":\"\",\"ownerDelegation\":\"80100000000000000000000\",\"name\":\"\",\"validatorFee\":\""
		+ "0.0\",\"registered\":true,\"ownerAddress\":\"ddx1qsp6qs6dnerl8jrzx4rhc7c6u6h96dzz6jd3js7zkaf2dr32gl3"
		+ "y03cxu5t3n\",\"isExternalStakeAccepted\":true,\"proposalsCompleted\":9868},{\"totalDelegatedStake\":"
		+ "\"80060000000000000000000\",\"uptimePercentage\":\"99.98\",\"proposalsMissed\":1,\"address\":\"dv1qf"
		+ "wtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumc3jtmxl\",\"infoURL\":\"\",\"ownerDelegation\":\"8"
		+ "0060000000000000000000\",\"name\":\"\",\"validatorFee\":\"0.0\",\"registered\":true,\"ownerAddress\""
		+ ":\"ddx1qsp9e00sv3h9md825wv0xe0jafaqu02pndlqxv8rnn5jhh0detz0n0qtp2phh\",\"isExternalStakeAccepted\":t"
		+ "rue,\"proposalsCompleted\":9862}]},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private static final String LOOKUP = "{\"result\":{\"totalDelegatedStake\":\"140100000000000000000000\",\"up"
		+ "timePercentage\":\"100.00\",\"proposalsMissed\":0,\"address\":\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897"
		+ "zk3gvt9uzh59rq9964vjryzf9\",\"infoURL\":\"\",\"ownerDelegation\":\"140100000000000000000000\",\"name\""
		+ ":\"\",\"validatorFee\":\"0.0\",\"registered\":true,\"ownerAddress\":\"ddx1qspll7tm6464am4yypzn59p42g6a"
		+ "8qhkguhc269p3vhs27s5vq5h24sfvvdfj\",\"isExternalStakeAccepted\":true,\"proposalsCompleted\":14968},\"i"
		+ "d\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	@Test
	public void testList() throws Exception {
		prepareClient(LIST)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.validator().list(10, Optional.empty())
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(validatorsResponse -> assertTrue(validatorsResponse.getCursor().isPresent()))
				.onSuccess(validatorsResponse -> assertEquals(5, validatorsResponse.getValidators().size())));
	}

	@Test
	public void testLookup() throws Exception {
		var address = ValidatorAddress.of(Addressing.ofNetworkId(99).forValidators()
											  .parse("dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9"));

		prepareClient(LOOKUP)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.validator().lookup(address)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(validator -> assertTrue(validator.isExternalStakeAccepted()))
				.onSuccess(validator -> assertEquals(amount(140100).tokens(), validator.getTotalDelegatedStake())));
	}
}
