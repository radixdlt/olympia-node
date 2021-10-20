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

package com.radixdlt.acceptance.staking;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.junit.Assert;

import com.radixdlt.api.dto.response.ValidatorDTO;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.test.RadixNetworkTest;

import java.util.List;
import java.util.Optional;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Staking extends RadixNetworkTest {

    private static final Logger logger = LogManager.getLogger();

    private List<ValidatorDTO> validatorsBuffer = Lists.newArrayList();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1, Amount.ofTokens(120));
    }

    @When("I request validator information")
    public void i_request_validator_information() {
        updateValidatorInformation();
        logger.info("Found {} validators", validatorsBuffer.size());
    }

    @Then("I observe that validators have stakes delegated to them")
    public void i_observe_that_validators_have_stakes_delegated_to_them() {
        var totalDelegatedStakeAcrossNetwork = validatorsBuffer.stream()
            .mapToDouble(value -> Double.parseDouble(value.getTotalDelegatedStake().toString())).sum();
        assertTrue("No stake was found in any validator, something is wrong with the test network",
            totalDelegatedStakeAcrossNetwork > 0);
    }

    @When("I stake {int}XRD to a validator")
    public void i_stake_xrd_to_a_validator(int stake) {
        updateValidatorInformation();
        // this test is hardcoded to use the 1st validator
        account1.stake(validatorsBuffer.get(0).getAddress(), Amount.ofTokens(stake));
    }

    @Then("I observe that the validator has {int}XRD more stake")
    public void i_observe_that_validator_having_xrd_more_stake(int stake) {
        Amount expectedStake = Amount.ofTokens(5);
        var previousStake = validatorsBuffer.get(0).getTotalDelegatedStake();
        updateValidatorInformation();
        var difference = validatorsBuffer.get(0).getTotalDelegatedStake().subtract(previousStake);
        assertEquals(difference, expectedStake.toSubunits());
    }

    @When("I unstake {int}XRD from the same validator")
    public void i_unstake_xrd_from_the_same_validator(Integer unstake) {
        throw new io.cucumber.java.PendingException();
    }

    @Then("I observe that my stake is unstaked and I got my tokens back")
    public void i_observe_that_my_stake_is_unstaked_and_i_got_my_tokens_back() {
        throw new io.cucumber.java.PendingException();
    }

    private void updateValidatorInformation() {
        validatorsBuffer.clear();
        validatorsBuffer = account1.validator().list(1000, Optional.empty()).getValidators();
        if (validatorsBuffer.isEmpty()) {
            Assert.fail("No validators were found in the network, test cannot proceed.");
        }
    }

}
