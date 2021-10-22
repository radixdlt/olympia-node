package com.radixdlt.test.account;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.identifiers.AID;

import java.util.Optional;

public interface RadixAccount {

    TransactionDTO lookup(AID txID);

    AID transfer(Account destination, Amount amount, Optional<String> message);

    AID stake(ValidatorAddress validatorAddress, Amount amount, Optional<String> message);

    AID unstake(ValidatorAddress validatorAddress, Amount amount, Optional<String> message);

    AID fixedSupplyToken(String symbol, String name, String description, String iconUrl, String tokenUrl, Amount supply);

    AID mutableSupplyToken(String symbol, String name, String desc, String iconUrl, String tokenUrl);
}
