/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.gateway;

import com.google.inject.Inject;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.api.gateway.openapitools.model.AccountIdentifier;
import com.radixdlt.api.gateway.openapitools.model.AccountTransaction;
import com.radixdlt.api.gateway.openapitools.model.AccountTransactionMetadata;
import com.radixdlt.api.gateway.openapitools.model.AccountTransactionStatus;
import com.radixdlt.api.gateway.openapitools.model.Action;
import com.radixdlt.api.gateway.openapitools.model.BelowMinimumStakeError;
import com.radixdlt.api.gateway.openapitools.model.BurnTokens;
import com.radixdlt.api.gateway.openapitools.model.CouldNotConstructFeesError;
import com.radixdlt.api.gateway.openapitools.model.CreateTokenDefinition;
import com.radixdlt.api.gateway.openapitools.model.GatewayError;
import com.radixdlt.api.gateway.openapitools.model.LedgerState;
import com.radixdlt.api.gateway.openapitools.model.MessageTooLongError;
import com.radixdlt.api.gateway.openapitools.model.MintTokens;
import com.radixdlt.api.gateway.openapitools.model.NotEnoughResourcesError;
import com.radixdlt.api.gateway.openapitools.model.CannotStakeError;
import com.radixdlt.api.gateway.openapitools.model.PublicKey;
import com.radixdlt.api.gateway.openapitools.model.StakeTokens;
import com.radixdlt.api.gateway.openapitools.model.TokenAmount;
import com.radixdlt.api.gateway.openapitools.model.TokenIdentifier;
import com.radixdlt.api.gateway.openapitools.model.TokenProperties;
import com.radixdlt.api.gateway.openapitools.model.TransactionBuild;
import com.radixdlt.api.gateway.openapitools.model.TransactionBuildRequest;
import com.radixdlt.api.gateway.openapitools.model.TransactionIdentifier;
import com.radixdlt.api.gateway.openapitools.model.TransactionRules;
import com.radixdlt.api.gateway.openapitools.model.TransferTokens;
import com.radixdlt.api.gateway.openapitools.model.UnstakeTokens;
import com.radixdlt.api.gateway.openapitools.model.ValidatorIdentifier;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.construction.DelegateStakePermissionException;
import com.radixdlt.application.tokens.construction.MinimumStakeException;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.MessageTooLongException;
import com.radixdlt.atom.NotEnoughResourcesException;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.UnsignedTxnData;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.engine.FeeConstructionException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GatewayModelMapper {
	private final Addressing addressing;

	@Inject
	public GatewayModelMapper(Addressing addressing) {
		this.addressing = addressing;
	}

	public ECPublicKey validator(ValidatorIdentifier validatorIdentifier) {
		return addressing.forValidators().parseOrThrow(
			validatorIdentifier.getAddress(), IllegalStateException::new
		);
	}

	public REAddr account(AccountIdentifier accountIdentifier) {
		return addressing.forAccounts().parseOrThrow(
			accountIdentifier.getAddress(), IllegalStateException::new
		);
	}

	public REAddr tokenAddress(TokenIdentifier tokenIdentifier) {
		return addressing.forResources().parseOrThrow(
			tokenIdentifier.getRri(), IllegalStateException::new
		).getSecond();
	}

	public ECPublicKey ecPublicKey(PublicKey publicKey) {
		try {
			return ECPublicKey.fromHex(publicKey.getHex());
		} catch (PublicKeyException e) {
			throw new IllegalStateException();
		}
	}

	public LedgerState ledgerState(LedgerProof ledgerProof) {
		return new LedgerState()
			.epoch(ledgerProof.getEpoch())
			.round(ledgerProof.getView().number())
			.timestamp(Instant.ofEpochMilli(ledgerProof.timestamp()).toString())
			.version(ledgerProof.getStateVersion());
	}

	public TransactionRules transactionRules(ForkConfig forkConfig) {
		return new TransactionRules()
			.maximumMessageLength(255)
			.minimumStake(new TokenAmount()
				.tokenIdentifier(nativeTokenIdentifier())
				.value(forkConfig.getConfig().getMinimumStake().toSubunits().toString())
			);
	}

	public List<Action> actions(
		REProcessedTxn txn,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		return txn.getGroupedStateUpdates()
			.stream()
			.flatMap(u -> inferActions(u, addressToSymbol, getValidatorStake).stream())
			.collect(Collectors.toList());
	}

	private CreateTokenDefinition inferCreateTokenDefinition(List<REStateUpdate> stateUpdates) {
		var tokenProperties = new TokenProperties();
		var tokenSupply = new TokenAmount().value("0");
		var createTokenDefinition = new CreateTokenDefinition()
			.tokenProperties(tokenProperties)
			.tokenSupply(tokenSupply);
		createTokenDefinition.setType("CreateTokenDefinition");

		for (var u : stateUpdates) {
			var substate = (Particle) u.getParsed();
			if (substate instanceof TokenResource tokenResource) {
				tokenResource.getOwner()
					.map(REAddr::ofPubKeyAccount)
					.map(GatewayModelMapper.this::accountIdentifier)
					.ifPresent(tokenProperties::setOwner);
				tokenProperties.granularity(tokenResource.getGranularity().toString());
				tokenProperties.isSupplyMutable(tokenResource.isMutable());
			} else if (substate instanceof TokenResourceMetadata tokenResourceMetadata) {
				tokenProperties.name(tokenResourceMetadata.getName());
				tokenProperties.description(tokenResourceMetadata.getDescription());
				tokenProperties.symbol(tokenResourceMetadata.getSymbol());
				tokenProperties.url(tokenResourceMetadata.getUrl());
				tokenProperties.iconUrl(tokenResourceMetadata.getIconUrl());
				tokenSupply.tokenIdentifier(tokenIdentifier(tokenResourceMetadata.getAddr(), tokenResourceMetadata.getSymbol()));
			} else if (substate instanceof TokensInAccount tokensInAccount) {
				// Can only mint a single substate for fixed supply tokens so we should
				// not need to worry about multiple holders
				createTokenDefinition.toAccount(accountIdentifier(tokensInAccount.getHoldingAddr()));
				tokenSupply.value(tokensInAccount.getAmount().toString());
			}
		}

		return createTokenDefinition;
	}

	private List<Action> inferActions(
		List<REStateUpdate> stateUpdates,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var accounting = REResourceAccounting.compute(stateUpdates.stream());
		var bucketAccounting = accounting.bucketAccounting();

		var withdrawals = bucketAccounting.entrySet().stream()
			.filter(e -> e.getValue().compareTo(BigInteger.ZERO) < 0)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		if (withdrawals.size() > 1) {
			throw new IllegalStateException("Invalid operation group with multiple vault withdrawals.");
		}

		if (stateUpdates.stream().anyMatch(u -> u.getParsed() instanceof TokenResource)) {
			if (withdrawals.size() > 0) {
				throw new IllegalStateException("Should be no withdrawals in Token Creation");
			}
			return List.of(inferCreateTokenDefinition(stateUpdates));
		}

		var deposits = bucketAccounting.entrySet().stream()
			.filter(e -> e.getValue().compareTo(BigInteger.ZERO) > 0)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		if (withdrawals.isEmpty() && deposits.isEmpty()) {
			return List.of();
		}

		if (withdrawals.isEmpty()) {
			return deposits.entrySet().stream()
				.filter(e -> e.getKey().resourceAddr() != null && !e.getKey().resourceAddr().isNativeToken())
				.map(e -> {
					var value = UInt256.from(e.getValue().toString());
					var amount = tokenAmount(value, e.getKey().resourceAddr(), addressToSymbol);
					return new MintTokens()
							.toAccount(accountIdentifier(e.getKey().getOwner()))
							.amount(amount)
							.type("MintTokens");
				})
				.collect(Collectors.toList());
		}

		var withdrawal = withdrawals.entrySet().stream().findFirst().orElseThrow();
		var from = withdrawal.getKey();
		var fromAmount = withdrawal.getValue().negate();
		var actions = new ArrayList<Action>();
		for (var e : deposits.entrySet()) {
			var to = e.getKey();
			var amount = e.getValue();
			fromAmount = fromAmount.subtract(amount);
			if (fromAmount.compareTo(BigInteger.ZERO) < 0) {
				throw new IllegalStateException("Invalid accounting found");
			}
			var value = UInt256.from(amount.toString());
			if (to.resourceAddr() != null && to.getValidatorKey() == null) {
				actions.add(new TransferTokens()
					.fromAccount(accountIdentifier(from.getOwner()))
					.toAccount(accountIdentifier(to.getOwner()))
					.amount(tokenAmount(value, to.resourceAddr(), addressToSymbol))
					.type("TransferTokens")
				);
			} else if (to.resourceAddr() != null && to.getValidatorKey() != null) {
				actions.add(new StakeTokens()
					.fromAccount(accountIdentifier(from.getOwner()))
					.toValidator(validatorIdentifier(to.getValidatorKey()))
					.amount(tokenAmount(value, to.resourceAddr(), addressToSymbol))
					.type("StakeTokens")
				);
			} else if (to.resourceAddr() == null && from.getValidatorKey() != null) {
				actions.add(new UnstakeTokens()
					.fromValidator(validatorIdentifier(from.getValidatorKey()))
					.toAccount(accountIdentifier(to.getOwner()))
					.amount(tokenAmount(value, from, to.resourceAddr(), addressToSymbol, (k, ownership) -> {
						var stakeData = getValidatorStake.apply(k);
						return ownership.multiply(stakeData.getTotalStake()).divide(stakeData.getTotalOwnership());
					}))
					.type("UnstakeTokens")
				);
			} else {
				throw new IllegalStateException();
			}
		}

		if (fromAmount.compareTo(BigInteger.ZERO) > 0 && from.resourceAddr() != null && !from.resourceAddr().isNativeToken()) {
			var value = UInt256.from(fromAmount.toString());
			actions.add(new BurnTokens()
				.fromAccount(accountIdentifier(from.getOwner()))
				.amount(tokenAmount(value, from.resourceAddr(), addressToSymbol))
				.type("BurnTokens")
			);
		}

		return actions;
	}

	public TokenIdentifier nativeTokenIdentifier() {
		var rri = addressing.forResources().of("xrd", REAddr.ofNativeToken());
		return new TokenIdentifier()
			.rri(rri);
	}

	public TransactionIdentifier transactionIdentifier(AID txnId) {
		return new TransactionIdentifier()
			.hash(txnId.toString());
	}

	public AccountIdentifier accountIdentifier(REAddr addr) {
		return new AccountIdentifier().address(addressing.forAccounts().of(addr));
	}

	public ValidatorIdentifier validatorIdentifier(ECPublicKey key) {
		return new ValidatorIdentifier().address(addressing.forValidators().of(key));
	}

	public TokenIdentifier tokenIdentifier(REAddr addr, String symbol) {
		var rri = addressing.forResources().of(symbol, addr);
		return new TokenIdentifier()
			.rri(rri);
	}

	public TokenIdentifier tokenIdentifier(REAddr addr, Function<REAddr, String> addrToSymbol) {
		var symbol = addrToSymbol.apply(addr);
		return tokenIdentifier(addr, symbol);
	}

	public TokenAmount tokenAmount(
		UInt256 amount,
		REAddr tokenAddress,
		Function<REAddr, String> addrToSymbol
	) {
		return new TokenAmount()
			.tokenIdentifier(tokenIdentifier(tokenAddress, addrToSymbol))
			.value(amount.toString());
	}

	public TokenAmount tokenAmount(
		UInt256 amount,
		Bucket fromBucket,
		REAddr tokenAddress,
		Function<REAddr, String> addrToSymbol,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		if (tokenAddress != null) {
			return tokenAmount(amount, tokenAddress, addrToSymbol);
		} else {
			var value = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amount)).getLow();
			return new TokenAmount()
				.tokenIdentifier(tokenIdentifier(REAddr.ofNativeToken(), addrToSymbol))
				.value(value.toString());
		}
	}

	public AccountTransaction accountTransaction(
		REProcessedTxn processedTxn,
		Instant confirmedTimestamp,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var accountTransaction = new AccountTransaction();

		// Only add actions for user transactions
		if (!processedTxn.getFeePaid().isZero()) {
			actions(processedTxn, addressToSymbol, getValidatorStake)
				.forEach(accountTransaction::addActionsItem);
		}

		accountTransaction.transactionIdentifier(transactionIdentifier(processedTxn.getTxnId()));
		if (confirmedTimestamp != null) {
			accountTransaction
				.transactionStatus(new AccountTransactionStatus()
					.status(AccountTransactionStatus.StatusEnum.CONFIRMED)
					.confirmedTime(confirmedTimestamp.toString())
				);
		} else {
			accountTransaction
				.transactionStatus(new AccountTransactionStatus()
					.status(AccountTransactionStatus.StatusEnum.PENDING)
				);
		}
		var metadata = new AccountTransactionMetadata()
			.hex(Bytes.toHexString(processedTxn.getTxn().getPayload()));
		processedTxn.getMsg().ifPresent(msg -> metadata.setMessage(Bytes.toHexString(msg)));
		accountTransaction.metadata(metadata);
		if (!processedTxn.getFeePaid().isZero()) {
			accountTransaction.feePaid(new TokenAmount()
				.tokenIdentifier(nativeTokenIdentifier())
				.value(processedTxn.getFeePaid().toString())
			);
		}

		return accountTransaction;
	}

	public TxAction txAction(Action action) {
		if (action instanceof TransferTokens transferTokens) {
			var from = account(transferTokens.getFromAccount());
			var to = account(transferTokens.getToAccount());
			var tokenAddress = tokenAddress(transferTokens.getAmount().getTokenIdentifier());
			var amount = UInt256.from(transferTokens.getAmount().getValue());
			return new TransferToken(tokenAddress, from, to, amount);
		} else if (action instanceof MintTokens mintTokens) {
			var to = account(mintTokens.getToAccount());
			var tokenAddress = tokenAddress(mintTokens.getAmount().getTokenIdentifier());
			var amount = UInt256.from(mintTokens.getAmount().getValue());
			return new MintToken(tokenAddress, to, amount);
		} else if (action instanceof BurnTokens burnTokens) {
			var from = account(burnTokens.getFromAccount());
			var tokenAddress = tokenAddress(burnTokens.getAmount().getTokenIdentifier());
			var amount = UInt256.from(burnTokens.getAmount().getValue());
			return new BurnToken(tokenAddress, from, amount);
		} else if (action instanceof StakeTokens stakeTokens) {
			var from = account(stakeTokens.getFromAccount());
			var to = validator(stakeTokens.getToValidator());
			var tokenAddress = tokenAddress(stakeTokens.getAmount().getTokenIdentifier());
			if (!tokenAddress.isNativeToken()) {
				throw new IllegalStateException();
			}
			var amount = UInt256.from(stakeTokens.getAmount().getValue());
			return new com.radixdlt.atom.actions.StakeTokens(from, to, amount);
		} else if (action instanceof UnstakeTokens unstakeTokens) {
			var from = validator(unstakeTokens.getFromValidator());
			var to = account(unstakeTokens.getToAccount());
			var tokenAddress = tokenAddress(unstakeTokens.getAmount().getTokenIdentifier());
			if (!tokenAddress.isNativeToken()) {
				throw new IllegalStateException();
			}
			var amount = UInt256.from(unstakeTokens.getAmount().getValue());
			return new com.radixdlt.atom.actions.UnstakeTokens(from, to, amount);
		} else if (action instanceof CreateTokenDefinition createTokenDefinition) {
			var tokenSupply = createTokenDefinition.getTokenSupply();
			var tokenAddress = tokenAddress(tokenSupply.getTokenIdentifier());
			var supply = UInt256.from(tokenSupply.getValue());
			var tokenProperties = createTokenDefinition.getTokenProperties();
			var symbol = tokenProperties.getSymbol();
			var name = tokenProperties.getName();
			var description = tokenProperties.getDescription();
			var iconUrl = tokenProperties.getIconUrl();
			var url = tokenProperties.getUrl();
			if (!tokenProperties.getIsSupplyMutable()) {
				if (supply.isZero() || tokenProperties.getOwner() != null) {
					throw new IllegalStateException();
				}

				var to = account(createTokenDefinition.getToAccount());
				return new CreateFixedToken(tokenAddress, to, symbol, name, description, iconUrl, url, supply);
			} else {
				if (!supply.isZero() || tokenProperties.getOwner() == null) {
					throw new IllegalStateException();
				}
				var owner = account(tokenProperties.getOwner());
				return new CreateMutableToken(tokenAddress, symbol, name, description, iconUrl, url, owner.publicKey().orElseThrow());
			}
		} else {
			throw new IllegalStateException();
		}
	}

	public TxnConstructionRequest txnConstructionRequest(TransactionBuildRequest request) {
		var constructionRequest = TxnConstructionRequest.create();
		constructionRequest.feePayer(account(request.getFeePayer()));
		var disableMintAndBurn = request.getDisableTokenMintAndBurn();
		if (disableMintAndBurn != null && !disableMintAndBurn) {
			constructionRequest.disableResourceAllocAndDestroy();
		}
		var message = request.getMessage();
		if (message != null) {
			constructionRequest.msg(Bytes.fromHexString(message));
		}

		var actions = request.getActions().stream().map(this::txAction).collect(Collectors.toList());
		constructionRequest.actions(actions);

		return constructionRequest;
	}

	public GatewayError transactionBuildError(TxBuilderException e) {
		if (e instanceof NotEnoughResourcesException notEnoughResourcesException) {
			return new NotEnoughResourcesError()
				.availableAmount(notEnoughResourcesException.getAvailable().toString())
				.requestedAmount(notEnoughResourcesException.getRequested().toString())
				.type(NotEnoughResourcesError.class.getSimpleName());
		} else if (e instanceof MinimumStakeException minimumStakeException) {
			return new BelowMinimumStakeError()
				.minimumAmount(minimumStakeException.getMinimumStake().toString())
				.requestedAmount(minimumStakeException.getAttempt().toString())
				.type(BelowMinimumStakeError.class.getSimpleName());
		} else if (e instanceof DelegateStakePermissionException delegateStakePermissionException) {
			return new CannotStakeError()
				.owner(accountIdentifier(delegateStakePermissionException.getOwner()))
				.user(accountIdentifier(delegateStakePermissionException.getUser()))
				.type(CannotStakeError.class.getSimpleName());
		} else if (e instanceof MessageTooLongException messageTooLongException) {
			return new MessageTooLongError()
				.lengthLimit(255)
				.attemptedLength(messageTooLongException.getAttemptedLength())
				.type(MessageTooLongError.class.getSimpleName());
		} else if (e instanceof FeeConstructionException feeConstructionException) {
			return new CouldNotConstructFeesError()
				.attempts(feeConstructionException.getAttempts())
				.type(CouldNotConstructFeesError.class.getSimpleName());
		} else {
			throw new IllegalStateException();
		}
	}

	public TransactionBuild transactionBuild(UnsignedTxnData unsignedTxnData)  {
		return new TransactionBuild()
			.fee(new TokenAmount()
				.tokenIdentifier(nativeTokenIdentifier())
				.value(unsignedTxnData.feesPaid().toString())
			)
			.unsignedTransaction(Bytes.toHexString(unsignedTxnData.blob()))
			.payloadToSign(Bytes.toHexString(unsignedTxnData.hashToSign().asBytes()));
	}
}
