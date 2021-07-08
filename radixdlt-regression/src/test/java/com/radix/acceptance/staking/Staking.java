/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radix.acceptance.staking;

import com.radix.acceptance.AcceptanceTest;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.junit.Assert;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Staking extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private List<ValidatorDTO> validatorsBuffer = Lists.newArrayList();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
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

    @Then("I observe that validator having {int}XRD more stake")
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
        validatorsBuffer = account1.validator().list(1000, NavigationCursor.create("")).getValidators();
        if (validatorsBuffer.isEmpty()) {
            Assert.fail("No validators were found in the network, test cannot proceed.");
        }
    }

}
