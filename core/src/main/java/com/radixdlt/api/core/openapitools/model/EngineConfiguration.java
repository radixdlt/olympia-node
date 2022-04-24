/*
 * Radix Core API
 * This API provides endpoints for Radix network integrators.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > mainly designed to be accessed in a private network for integration use.  Welcome to the Radix Core API version 0.9.0 for Integrators. Version 0.9.0 is intended for integrators who wish to begin the process of developing an integration between the Radix ledger and their own systems.  The Core API is separated into two: * The **Data API** is a read-only api which allows integrators to view and sync to the state of the ledger. * The **Construction API** allows integrators to construct and submit a transaction to the network on behalf of a key holder.  The Core API is primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  The Core API is not a full replacement for the current Node and Archive [APIs](https://docs.radixdlt.com). We are also working on a public-facing Gateway API that will be part of a full \"new API\", but is yet to be finalised.  We should stress that this API is in preview, and should __not__ be deployed into production until version 1.0.0 has been finalised in an official Radix node release.  ## Backwards Compatibility  The OpenAPI specification of all endpoints in Version 0.9.0 is intended to be backwards compatible with version 1.0.0 once released, so that there is little risk that clients working with this spec will break after the release of 1.0.0. Additional endpoints (such as retrieving mempool contents) are planned to be added.  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Client Reference Implementation  > IMPORTANT > > The Network Gateway service is subject to substantial change before official release in v1.  We are currently working on a client reference implementation to the Core API, which we are happy to share with you for reference, as a demonstration of how to interpret responses from the Core API:  * [Latest - more functionality, no guarantees of correctness](https://github.com/radixdlt/radixdlt-network-gateway/) * [Stable - old code, ingesting balance and transfer data, manually tested against stokenet](https://github.com/radixdlt/radixdlt-network-gateway/tree/v0.1_BalanceSubstatesAndHistory)  As a starter, check out the folder `./src/DataAggregator/LedgerExtension` for understanding how to parse the contents of the transaction stream.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/api.yaml) in the core folder. See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  Integrators can make use of the Data API to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  Integrators can make use of the Construction API to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release. 
 *
 * The version of the OpenAPI document: 0.9.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.core.openapitools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * EngineConfiguration
 */
@JsonPropertyOrder({
  EngineConfiguration.JSON_PROPERTY_NATIVE_TOKEN,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_VALIDATORS,
  EngineConfiguration.JSON_PROPERTY_TOKEN_SYMBOL_PATTERN,
  EngineConfiguration.JSON_PROPERTY_UNSTAKING_DELAY_EPOCH_LENGTH,
  EngineConfiguration.JSON_PROPERTY_MINIMUM_COMPLETED_PROPOSALS_PERCENTAGE,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_TRANSACTION_SIZE,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_TRANSACTIONS_PER_ROUND,
  EngineConfiguration.JSON_PROPERTY_VALIDATOR_FEE_INCREASE_DEBOUNCER_EPOCH_LENGTH,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_ROUNDS_PER_EPOCH,
  EngineConfiguration.JSON_PROPERTY_MAXIMUM_VALIDATOR_FEE_INCREASE,
  EngineConfiguration.JSON_PROPERTY_MINIMUM_STAKE,
  EngineConfiguration.JSON_PROPERTY_REWARDS_PER_PROPOSAL,
  EngineConfiguration.JSON_PROPERTY_RESERVED_SYMBOLS,
  EngineConfiguration.JSON_PROPERTY_FEE_TABLE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-12-06T21:41:23.542373-06:00[America/Chicago]")
public class EngineConfiguration {
  public static final String JSON_PROPERTY_NATIVE_TOKEN = "native_token";
  private TokenResourceIdentifier nativeToken;

  public static final String JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH = "maximum_message_length";
  private Integer maximumMessageLength;

  public static final String JSON_PROPERTY_MAXIMUM_VALIDATORS = "maximum_validators";
  private Integer maximumValidators;

  public static final String JSON_PROPERTY_TOKEN_SYMBOL_PATTERN = "token_symbol_pattern";
  private String tokenSymbolPattern;

  public static final String JSON_PROPERTY_UNSTAKING_DELAY_EPOCH_LENGTH = "unstaking_delay_epoch_length";
  private Long unstakingDelayEpochLength;

  public static final String JSON_PROPERTY_MINIMUM_COMPLETED_PROPOSALS_PERCENTAGE = "minimum_completed_proposals_percentage";
  private Integer minimumCompletedProposalsPercentage;

  public static final String JSON_PROPERTY_MAXIMUM_TRANSACTION_SIZE = "maximum_transaction_size";
  private Long maximumTransactionSize;

  public static final String JSON_PROPERTY_MAXIMUM_TRANSACTIONS_PER_ROUND = "maximum_transactions_per_round";
  private Integer maximumTransactionsPerRound;

  public static final String JSON_PROPERTY_VALIDATOR_FEE_INCREASE_DEBOUNCER_EPOCH_LENGTH = "validator_fee_increase_debouncer_epoch_length";
  private Long validatorFeeIncreaseDebouncerEpochLength;

  public static final String JSON_PROPERTY_MAXIMUM_ROUNDS_PER_EPOCH = "maximum_rounds_per_epoch";
  private Long maximumRoundsPerEpoch;

  public static final String JSON_PROPERTY_MAXIMUM_VALIDATOR_FEE_INCREASE = "maximum_validator_fee_increase";
  private Integer maximumValidatorFeeIncrease;

  public static final String JSON_PROPERTY_MINIMUM_STAKE = "minimum_stake";
  private ResourceAmount minimumStake;

  public static final String JSON_PROPERTY_REWARDS_PER_PROPOSAL = "rewards_per_proposal";
  private ResourceAmount rewardsPerProposal;

  public static final String JSON_PROPERTY_RESERVED_SYMBOLS = "reserved_symbols";
  private List<String> reservedSymbols = new ArrayList<>();

  public static final String JSON_PROPERTY_FEE_TABLE = "fee_table";
  private FeeTable feeTable;


  public EngineConfiguration nativeToken(TokenResourceIdentifier nativeToken) {
    this.nativeToken = nativeToken;
    return this;
  }

   /**
   * Get nativeToken
   * @return nativeToken
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_NATIVE_TOKEN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public TokenResourceIdentifier getNativeToken() {
    return nativeToken;
  }


  @JsonProperty(JSON_PROPERTY_NATIVE_TOKEN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setNativeToken(TokenResourceIdentifier nativeToken) {
    this.nativeToken = nativeToken;
  }


  public EngineConfiguration maximumMessageLength(Integer maximumMessageLength) {
    this.maximumMessageLength = maximumMessageLength;
    return this;
  }

   /**
   * Get maximumMessageLength
   * @return maximumMessageLength
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaximumMessageLength() {
    return maximumMessageLength;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumMessageLength(Integer maximumMessageLength) {
    this.maximumMessageLength = maximumMessageLength;
  }


  public EngineConfiguration maximumValidators(Integer maximumValidators) {
    this.maximumValidators = maximumValidators;
    return this;
  }

   /**
   * Get maximumValidators
   * @return maximumValidators
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_VALIDATORS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaximumValidators() {
    return maximumValidators;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_VALIDATORS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumValidators(Integer maximumValidators) {
    this.maximumValidators = maximumValidators;
  }


  public EngineConfiguration tokenSymbolPattern(String tokenSymbolPattern) {
    this.tokenSymbolPattern = tokenSymbolPattern;
    return this;
  }

   /**
   * Get tokenSymbolPattern
   * @return tokenSymbolPattern
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_TOKEN_SYMBOL_PATTERN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getTokenSymbolPattern() {
    return tokenSymbolPattern;
  }


  @JsonProperty(JSON_PROPERTY_TOKEN_SYMBOL_PATTERN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setTokenSymbolPattern(String tokenSymbolPattern) {
    this.tokenSymbolPattern = tokenSymbolPattern;
  }


  public EngineConfiguration unstakingDelayEpochLength(Long unstakingDelayEpochLength) {
    this.unstakingDelayEpochLength = unstakingDelayEpochLength;
    return this;
  }

   /**
   * Get unstakingDelayEpochLength
   * @return unstakingDelayEpochLength
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_UNSTAKING_DELAY_EPOCH_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Long getUnstakingDelayEpochLength() {
    return unstakingDelayEpochLength;
  }


  @JsonProperty(JSON_PROPERTY_UNSTAKING_DELAY_EPOCH_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setUnstakingDelayEpochLength(Long unstakingDelayEpochLength) {
    this.unstakingDelayEpochLength = unstakingDelayEpochLength;
  }


  public EngineConfiguration minimumCompletedProposalsPercentage(Integer minimumCompletedProposalsPercentage) {
    this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
    return this;
  }

   /**
   * Get minimumCompletedProposalsPercentage
   * @return minimumCompletedProposalsPercentage
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MINIMUM_COMPLETED_PROPOSALS_PERCENTAGE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMinimumCompletedProposalsPercentage() {
    return minimumCompletedProposalsPercentage;
  }


  @JsonProperty(JSON_PROPERTY_MINIMUM_COMPLETED_PROPOSALS_PERCENTAGE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMinimumCompletedProposalsPercentage(Integer minimumCompletedProposalsPercentage) {
    this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
  }


  public EngineConfiguration maximumTransactionSize(Long maximumTransactionSize) {
    this.maximumTransactionSize = maximumTransactionSize;
    return this;
  }

   /**
   * Get maximumTransactionSize
   * @return maximumTransactionSize
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_TRANSACTION_SIZE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Long getMaximumTransactionSize() {
    return maximumTransactionSize;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_TRANSACTION_SIZE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumTransactionSize(Long maximumTransactionSize) {
    this.maximumTransactionSize = maximumTransactionSize;
  }


  public EngineConfiguration maximumTransactionsPerRound(Integer maximumTransactionsPerRound) {
    this.maximumTransactionsPerRound = maximumTransactionsPerRound;
    return this;
  }

   /**
   * Get maximumTransactionsPerRound
   * @return maximumTransactionsPerRound
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_TRANSACTIONS_PER_ROUND)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaximumTransactionsPerRound() {
    return maximumTransactionsPerRound;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_TRANSACTIONS_PER_ROUND)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumTransactionsPerRound(Integer maximumTransactionsPerRound) {
    this.maximumTransactionsPerRound = maximumTransactionsPerRound;
  }


  public EngineConfiguration validatorFeeIncreaseDebouncerEpochLength(Long validatorFeeIncreaseDebouncerEpochLength) {
    this.validatorFeeIncreaseDebouncerEpochLength = validatorFeeIncreaseDebouncerEpochLength;
    return this;
  }

   /**
   * Get validatorFeeIncreaseDebouncerEpochLength
   * @return validatorFeeIncreaseDebouncerEpochLength
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_VALIDATOR_FEE_INCREASE_DEBOUNCER_EPOCH_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Long getValidatorFeeIncreaseDebouncerEpochLength() {
    return validatorFeeIncreaseDebouncerEpochLength;
  }


  @JsonProperty(JSON_PROPERTY_VALIDATOR_FEE_INCREASE_DEBOUNCER_EPOCH_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setValidatorFeeIncreaseDebouncerEpochLength(Long validatorFeeIncreaseDebouncerEpochLength) {
    this.validatorFeeIncreaseDebouncerEpochLength = validatorFeeIncreaseDebouncerEpochLength;
  }


  public EngineConfiguration maximumRoundsPerEpoch(Long maximumRoundsPerEpoch) {
    this.maximumRoundsPerEpoch = maximumRoundsPerEpoch;
    return this;
  }

   /**
   * Get maximumRoundsPerEpoch
   * @return maximumRoundsPerEpoch
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_ROUNDS_PER_EPOCH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Long getMaximumRoundsPerEpoch() {
    return maximumRoundsPerEpoch;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_ROUNDS_PER_EPOCH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumRoundsPerEpoch(Long maximumRoundsPerEpoch) {
    this.maximumRoundsPerEpoch = maximumRoundsPerEpoch;
  }


  public EngineConfiguration maximumValidatorFeeIncrease(Integer maximumValidatorFeeIncrease) {
    this.maximumValidatorFeeIncrease = maximumValidatorFeeIncrease;
    return this;
  }

   /**
   * Get maximumValidatorFeeIncrease
   * @return maximumValidatorFeeIncrease
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_VALIDATOR_FEE_INCREASE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaximumValidatorFeeIncrease() {
    return maximumValidatorFeeIncrease;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_VALIDATOR_FEE_INCREASE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMaximumValidatorFeeIncrease(Integer maximumValidatorFeeIncrease) {
    this.maximumValidatorFeeIncrease = maximumValidatorFeeIncrease;
  }


  public EngineConfiguration minimumStake(ResourceAmount minimumStake) {
    this.minimumStake = minimumStake;
    return this;
  }

   /**
   * Get minimumStake
   * @return minimumStake
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_MINIMUM_STAKE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public ResourceAmount getMinimumStake() {
    return minimumStake;
  }


  @JsonProperty(JSON_PROPERTY_MINIMUM_STAKE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setMinimumStake(ResourceAmount minimumStake) {
    this.minimumStake = minimumStake;
  }


  public EngineConfiguration rewardsPerProposal(ResourceAmount rewardsPerProposal) {
    this.rewardsPerProposal = rewardsPerProposal;
    return this;
  }

   /**
   * Get rewardsPerProposal
   * @return rewardsPerProposal
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_REWARDS_PER_PROPOSAL)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public ResourceAmount getRewardsPerProposal() {
    return rewardsPerProposal;
  }


  @JsonProperty(JSON_PROPERTY_REWARDS_PER_PROPOSAL)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setRewardsPerProposal(ResourceAmount rewardsPerProposal) {
    this.rewardsPerProposal = rewardsPerProposal;
  }


  public EngineConfiguration reservedSymbols(List<String> reservedSymbols) {
    this.reservedSymbols = reservedSymbols;
    return this;
  }

  public EngineConfiguration addReservedSymbolsItem(String reservedSymbolsItem) {
    this.reservedSymbols.add(reservedSymbolsItem);
    return this;
  }

   /**
   * Get reservedSymbols
   * @return reservedSymbols
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_RESERVED_SYMBOLS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<String> getReservedSymbols() {
    return reservedSymbols;
  }


  @JsonProperty(JSON_PROPERTY_RESERVED_SYMBOLS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setReservedSymbols(List<String> reservedSymbols) {
    this.reservedSymbols = reservedSymbols;
  }


  public EngineConfiguration feeTable(FeeTable feeTable) {
    this.feeTable = feeTable;
    return this;
  }

   /**
   * Get feeTable
   * @return feeTable
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_FEE_TABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public FeeTable getFeeTable() {
    return feeTable;
  }


  @JsonProperty(JSON_PROPERTY_FEE_TABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setFeeTable(FeeTable feeTable) {
    this.feeTable = feeTable;
  }


  /**
   * Return true if this EngineConfiguration object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EngineConfiguration engineConfiguration = (EngineConfiguration) o;
    return Objects.equals(this.nativeToken, engineConfiguration.nativeToken) &&
        Objects.equals(this.maximumMessageLength, engineConfiguration.maximumMessageLength) &&
        Objects.equals(this.maximumValidators, engineConfiguration.maximumValidators) &&
        Objects.equals(this.tokenSymbolPattern, engineConfiguration.tokenSymbolPattern) &&
        Objects.equals(this.unstakingDelayEpochLength, engineConfiguration.unstakingDelayEpochLength) &&
        Objects.equals(this.minimumCompletedProposalsPercentage, engineConfiguration.minimumCompletedProposalsPercentage) &&
        Objects.equals(this.maximumTransactionSize, engineConfiguration.maximumTransactionSize) &&
        Objects.equals(this.maximumTransactionsPerRound, engineConfiguration.maximumTransactionsPerRound) &&
        Objects.equals(this.validatorFeeIncreaseDebouncerEpochLength, engineConfiguration.validatorFeeIncreaseDebouncerEpochLength) &&
        Objects.equals(this.maximumRoundsPerEpoch, engineConfiguration.maximumRoundsPerEpoch) &&
        Objects.equals(this.maximumValidatorFeeIncrease, engineConfiguration.maximumValidatorFeeIncrease) &&
        Objects.equals(this.minimumStake, engineConfiguration.minimumStake) &&
        Objects.equals(this.rewardsPerProposal, engineConfiguration.rewardsPerProposal) &&
        Objects.equals(this.reservedSymbols, engineConfiguration.reservedSymbols) &&
        Objects.equals(this.feeTable, engineConfiguration.feeTable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nativeToken, maximumMessageLength, maximumValidators, tokenSymbolPattern, unstakingDelayEpochLength, minimumCompletedProposalsPercentage, maximumTransactionSize, maximumTransactionsPerRound, validatorFeeIncreaseDebouncerEpochLength, maximumRoundsPerEpoch, maximumValidatorFeeIncrease, minimumStake, rewardsPerProposal, reservedSymbols, feeTable);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EngineConfiguration {\n");
    sb.append("    nativeToken: ").append(toIndentedString(nativeToken)).append("\n");
    sb.append("    maximumMessageLength: ").append(toIndentedString(maximumMessageLength)).append("\n");
    sb.append("    maximumValidators: ").append(toIndentedString(maximumValidators)).append("\n");
    sb.append("    tokenSymbolPattern: ").append(toIndentedString(tokenSymbolPattern)).append("\n");
    sb.append("    unstakingDelayEpochLength: ").append(toIndentedString(unstakingDelayEpochLength)).append("\n");
    sb.append("    minimumCompletedProposalsPercentage: ").append(toIndentedString(minimumCompletedProposalsPercentage)).append("\n");
    sb.append("    maximumTransactionSize: ").append(toIndentedString(maximumTransactionSize)).append("\n");
    sb.append("    maximumTransactionsPerRound: ").append(toIndentedString(maximumTransactionsPerRound)).append("\n");
    sb.append("    validatorFeeIncreaseDebouncerEpochLength: ").append(toIndentedString(validatorFeeIncreaseDebouncerEpochLength)).append("\n");
    sb.append("    maximumRoundsPerEpoch: ").append(toIndentedString(maximumRoundsPerEpoch)).append("\n");
    sb.append("    maximumValidatorFeeIncrease: ").append(toIndentedString(maximumValidatorFeeIncrease)).append("\n");
    sb.append("    minimumStake: ").append(toIndentedString(minimumStake)).append("\n");
    sb.append("    rewardsPerProposal: ").append(toIndentedString(rewardsPerProposal)).append("\n");
    sb.append("    reservedSymbols: ").append(toIndentedString(reservedSymbols)).append("\n");
    sb.append("    feeTable: ").append(toIndentedString(feeTable)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

