/*
 * Radix Core API
 * This API provides endpoints for Radix network integrators.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > mainly designed to be accessed in a private network for integration use.  Welcome to the Radix Core API version 0.9.0 for Integrators. Version 0.9.0 is intended for integrators who wish to begin the process of developing an integration between the Radix ledger and their own systems.  The Core API is separated into two: * The **Data API** is a read-only api which allows integrators to view and sync to the state of the ledger. * The **Construction API** allows integrators to construct and submit a transaction to the network on behalf of a key holder.  The Core API is primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  The Core API is not a full replacement for the current Node and Archive [APIs](https://docs.radixdlt.com). We are also working on a public-facing Gateway API that will be part of a full \"new API\", but is yet to be finalised.  We should stress that this API is in preview, and should __not__ be deployed into production until version 1.0.0 has been finalised in an official Radix node release.  ## Backwards Compatibility  The OpenAPI specification of all endpoints in Version 0.9.0 is intended to be backwards compatible with version 1.0.0 once released, so that there is little risk that clients working with this spec will break after the release of 1.0.0. Additional endpoints (such as retrieving mempool contents) are planned to be added.  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Client Reference Implementation  > IMPORTANT > > The Network Gateway service is subject to substantial change before official release in v1.  We are currently working on a client reference implementation to the Core API, which we are happy to share with you for reference, as a demonstration of how to interpret responses from the Core API:  * [Latest - more functionality, no guarantees of correctness](https://github.com/radixdlt/radixdlt-network-gateway/) * [Stable - old code, ingesting balance and transfer data, manually tested against stokenet](https://github.com/radixdlt/radixdlt-network-gateway/tree/v0.1_BalanceSubstatesAndHistory)  As a starter, check out the folder `./src/DataAggregator/LedgerExtension` for understanding how to parse the contents of the transaction stream.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/api.yaml) in the core folder. See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  Integrators can make use of the Data API to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  Integrators can make use of the Construction API to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release. 
 *
 * The version of the OpenAPI document: 0.9.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.core.core.openapitools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.radixdlt.api.core.core.openapitools.JSON;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * NotEnoughResourcesErrorDetails
 */
@JsonPropertyOrder({
  NotEnoughResourcesErrorDetails.JSON_PROPERTY_AVAILABLE,
  NotEnoughResourcesErrorDetails.JSON_PROPERTY_ATTEMPTED_TO_TAKE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-11-29T20:27:18.932829-06:00[America/Chicago]")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = BelowMinimumStakeErrorDetails.class, name = "BelowMinimumStakeErrorDetails"),
  @JsonSubTypes.Type(value = DataObjectNotSupportedByEntityErrorDetails.class, name = "DataObjectNotSupportedByEntityErrorDetails"),
  @JsonSubTypes.Type(value = FeeConstructionErrorDetails.class, name = "FeeConstructionErrorDetails"),
  @JsonSubTypes.Type(value = InternalServerErrorDetails.class, name = "InternalServerErrorDetails"),
  @JsonSubTypes.Type(value = InvalidAddressErrorDetails.class, name = "InvalidAddressErrorDetails"),
  @JsonSubTypes.Type(value = InvalidDataObjectErrorDetails.class, name = "InvalidDataObjectErrorDetails"),
  @JsonSubTypes.Type(value = InvalidFeePayerEntityErrorDetails.class, name = "InvalidFeePayerEntityErrorDetails"),
  @JsonSubTypes.Type(value = InvalidHexErrorDetails.class, name = "InvalidHexErrorDetails"),
  @JsonSubTypes.Type(value = InvalidJsonDetails.class, name = "InvalidJsonDetails"),
  @JsonSubTypes.Type(value = InvalidPartialStateIdentifierErrorDetails.class, name = "InvalidPartialStateIdentifierErrorDetails"),
  @JsonSubTypes.Type(value = InvalidPublicKeyErrorDetails.class, name = "InvalidPublicKeyErrorDetails"),
  @JsonSubTypes.Type(value = InvalidSignatureErrorDetails.class, name = "InvalidSignatureErrorDetails"),
  @JsonSubTypes.Type(value = InvalidSubEntityErrorDetails.class, name = "InvalidSubEntityErrorDetails"),
  @JsonSubTypes.Type(value = InvalidTransactionErrorDetails.class, name = "InvalidTransactionErrorDetails"),
  @JsonSubTypes.Type(value = InvalidTransactionHashErrorDetails.class, name = "InvalidTransactionHashErrorDetails"),
  @JsonSubTypes.Type(value = MessageTooLongErrorDetails.class, name = "MessageTooLongErrorDetails"),
  @JsonSubTypes.Type(value = NetworkNotSupportedErrorDetails.class, name = "NetworkNotSupportedErrorDetails"),
  @JsonSubTypes.Type(value = NotEnoughResourcesErrorDetails.class, name = "NotEnoughResourcesErrorDetails"),
  @JsonSubTypes.Type(value = NotValidatorOwnerErrorDetails.class, name = "NotValidatorOwnerErrorDetails"),
  @JsonSubTypes.Type(value = PublicKeyNotSupportedErrorDetails.class, name = "PublicKeyNotSupportedErrorDetails"),
  @JsonSubTypes.Type(value = ResourceDepositOperationNotSupportedByEntityErrorDetails.class, name = "ResourceDepositOperationNotSupportedByEntityErrorDetails"),
  @JsonSubTypes.Type(value = ResourceWithdrawOperationNotSupportedByEntityErrorDetails.class, name = "ResourceWithdrawOperationNotSupportedByEntityErrorDetails"),
  @JsonSubTypes.Type(value = StateIdentifierNotFoundErrorDetails.class, name = "StateIdentifierNotFoundErrorDetails"),
  @JsonSubTypes.Type(value = SubstateDependencyNotFoundErrorDetails.class, name = "SubstateDependencyNotFoundErrorDetails"),
  @JsonSubTypes.Type(value = TransactionNotFoundErrorDetails.class, name = "TransactionNotFoundErrorDetails"),
})

public class NotEnoughResourcesErrorDetails extends ErrorDetails {
  public static final String JSON_PROPERTY_AVAILABLE = "available";
  private ResourceAmount available;

  public static final String JSON_PROPERTY_ATTEMPTED_TO_TAKE = "attempted_to_take";
  private ResourceAmount attemptedToTake;


  public NotEnoughResourcesErrorDetails available(ResourceAmount available) {
    this.available = available;
    return this;
  }

   /**
   * Get available
   * @return available
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_AVAILABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public ResourceAmount getAvailable() {
    return available;
  }


  @JsonProperty(JSON_PROPERTY_AVAILABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setAvailable(ResourceAmount available) {
    this.available = available;
  }


  public NotEnoughResourcesErrorDetails attemptedToTake(ResourceAmount attemptedToTake) {
    this.attemptedToTake = attemptedToTake;
    return this;
  }

   /**
   * Get attemptedToTake
   * @return attemptedToTake
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_ATTEMPTED_TO_TAKE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public ResourceAmount getAttemptedToTake() {
    return attemptedToTake;
  }


  @JsonProperty(JSON_PROPERTY_ATTEMPTED_TO_TAKE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setAttemptedToTake(ResourceAmount attemptedToTake) {
    this.attemptedToTake = attemptedToTake;
  }


  /**
   * Return true if this NotEnoughResourcesErrorDetails object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NotEnoughResourcesErrorDetails notEnoughResourcesErrorDetails = (NotEnoughResourcesErrorDetails) o;
    return Objects.equals(this.available, notEnoughResourcesErrorDetails.available) &&
        Objects.equals(this.attemptedToTake, notEnoughResourcesErrorDetails.attemptedToTake) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(available, attemptedToTake, super.hashCode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NotEnoughResourcesErrorDetails {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    available: ").append(toIndentedString(available)).append("\n");
    sb.append("    attemptedToTake: ").append(toIndentedString(attemptedToTake)).append("\n");
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

static {
  // Initialize and register the discriminator mappings.
  Map<String, Class<?>> mappings = new HashMap<String, Class<?>>();
  mappings.put("BelowMinimumStakeErrorDetails", BelowMinimumStakeErrorDetails.class);
  mappings.put("DataObjectNotSupportedByEntityErrorDetails", DataObjectNotSupportedByEntityErrorDetails.class);
  mappings.put("FeeConstructionErrorDetails", FeeConstructionErrorDetails.class);
  mappings.put("InternalServerErrorDetails", InternalServerErrorDetails.class);
  mappings.put("InvalidAddressErrorDetails", InvalidAddressErrorDetails.class);
  mappings.put("InvalidDataObjectErrorDetails", InvalidDataObjectErrorDetails.class);
  mappings.put("InvalidFeePayerEntityErrorDetails", InvalidFeePayerEntityErrorDetails.class);
  mappings.put("InvalidHexErrorDetails", InvalidHexErrorDetails.class);
  mappings.put("InvalidJsonDetails", InvalidJsonDetails.class);
  mappings.put("InvalidPartialStateIdentifierErrorDetails", InvalidPartialStateIdentifierErrorDetails.class);
  mappings.put("InvalidPublicKeyErrorDetails", InvalidPublicKeyErrorDetails.class);
  mappings.put("InvalidSignatureErrorDetails", InvalidSignatureErrorDetails.class);
  mappings.put("InvalidSubEntityErrorDetails", InvalidSubEntityErrorDetails.class);
  mappings.put("InvalidTransactionErrorDetails", InvalidTransactionErrorDetails.class);
  mappings.put("InvalidTransactionHashErrorDetails", InvalidTransactionHashErrorDetails.class);
  mappings.put("MessageTooLongErrorDetails", MessageTooLongErrorDetails.class);
  mappings.put("NetworkNotSupportedErrorDetails", NetworkNotSupportedErrorDetails.class);
  mappings.put("NotEnoughResourcesErrorDetails", NotEnoughResourcesErrorDetails.class);
  mappings.put("NotValidatorOwnerErrorDetails", NotValidatorOwnerErrorDetails.class);
  mappings.put("PublicKeyNotSupportedErrorDetails", PublicKeyNotSupportedErrorDetails.class);
  mappings.put("ResourceDepositOperationNotSupportedByEntityErrorDetails", ResourceDepositOperationNotSupportedByEntityErrorDetails.class);
  mappings.put("ResourceWithdrawOperationNotSupportedByEntityErrorDetails", ResourceWithdrawOperationNotSupportedByEntityErrorDetails.class);
  mappings.put("StateIdentifierNotFoundErrorDetails", StateIdentifierNotFoundErrorDetails.class);
  mappings.put("SubstateDependencyNotFoundErrorDetails", SubstateDependencyNotFoundErrorDetails.class);
  mappings.put("TransactionNotFoundErrorDetails", TransactionNotFoundErrorDetails.class);
  mappings.put("NotEnoughResourcesErrorDetails", NotEnoughResourcesErrorDetails.class);
  JSON.registerDiscriminator(NotEnoughResourcesErrorDetails.class, "type", mappings);
}
}

