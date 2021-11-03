package com.radixdlt.acceptance.multi_action_transactions;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.utils.TransactionUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class MultiActionTransactions extends RadixNetworkTest {

    private static final Amount AMOUNT_TO_TRANSFER = Amount.ofTokens(2);
    private static final Amount STAKE_AMOUNT = Amount.ofTokens(90);

    @Given("I have an account with {int} XRD at a suitable Radix network")
    public void i_have_an_account_with_xrd_at_a_suitable_radix_network(int tokens) {
        faucet(account1, Amount.ofTokens(tokens));
    }

    @Given("I cannot submit a transaction with two actions: transfer {int} XRD to account2 and transfer {int} XRD to account3")
    public void i_submit_a_transaction_with_two_actions_transfer_xrd_to_account2_and_transfer_xrd_to_account3(int tokensTo2, int tokensTo3) {
        var nativeRri = account1.getNativeToken().getRri();
        Assertions.runExpectingRadixApiException(() -> {
            var request = TransactionRequest.createBuilder(account1.getAddress())
                .transfer(account1.getAddress(), account2.getAddress(), Amount.ofTokens(tokensTo2).toSubunits(), nativeRri)
                .transfer(account1.getAddress(), getTestAccount(2).getAddress(), Amount.ofTokens(tokensTo3).toSubunits(), nativeRri)
                .build();
            txBuffer = TransactionUtils.buildFinalizeAndSubmitTransaction(account1, request, true);
        }, "Not enough balance for transfer");
    }

    @Given("I submit a transaction with three actions: two transfers and one staking")
    public void i_submit_a_transaction_with_three_actions_two_transfers_and_one_staking() {
        var nativeRri = account1.getNativeToken().getRri();
        var account1Address = account1.getAddress();
        var validatorAddress = account1.validator().list(100, Optional.empty()).getValidators().get(0).getAddress();
        var request = TransactionRequest.createBuilder(account1Address)
            .transfer(account1Address, account2.getAddress(), AMOUNT_TO_TRANSFER.toSubunits(), nativeRri)
            .transfer(account1Address, account2.getAddress(), AMOUNT_TO_TRANSFER.toSubunits(), nativeRri)
            .stake(account1Address, validatorAddress, STAKE_AMOUNT.toSubunits())
            .build();
        txBuffer = TransactionUtils.buildFinalizeAndSubmitTransaction(account1, request, true);
    }

    @Then("I can observe the actions taking effect")
    public void i_can_observe_the_actions_taking_effect() {
        // this is a bit hardcoded. if more scenariors are addded, we will need to parameterized
        var actions = account1.lookup(txBuffer).getActions();
        assertEquals(actions.get(0).getAmount().get(), AMOUNT_TO_TRANSFER.toSubunits());
        assertEquals(actions.get(1).getAmount().get(), AMOUNT_TO_TRANSFER.toSubunits());
        assertEquals(actions.get(2).getAmount().get(), STAKE_AMOUNT.toSubunits());
    }

}
