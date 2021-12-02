/*
 * Radix Core API
 * This API provides endpoints for Radix network integrators.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > mainly designed to be accessed in a private network for integration use.  Welcome to the Radix Core API version 0.9.0 for Integrators. Version 0.9.0 is intended for integrators who wish to begin the process of developing an integration between the Radix ledger and their own systems.  The Core API is separated into two: * The **Data API** is a read-only api which allows integrators to view and sync to the state of the ledger. * The **Construction API** allows integrators to construct and submit a transaction to the network on behalf of a key holder.  The Core API is primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  The Core API is not a full replacement for the current Node and Archive [APIs](https://docs.radixdlt.com). We are also working on a public-facing Gateway API that will be part of a full \"new API\", but is yet to be finalised.  We should stress that this API is in preview, and should __not__ be deployed into production until version 1.0.0 has been finalised in an official Radix node release.  ## Backwards Compatibility  The OpenAPI specification of all endpoints in Version 0.9.0 is intended to be backwards compatible with version 1.0.0 once released, so that there is little risk that clients working with this spec will break after the release of 1.0.0. Additional endpoints (such as retrieving mempool contents) are planned to be added.  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Client Reference Implementation  We are currently working on a client reference implementation to the Core API and hope to have this available to share with you soon.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/api.yaml) in the core folder. See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  Integrators can make use of the Data API to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  Integrators can make use of the Construction API to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release. 
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
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;


/**
 * ConstructionFinalizeRequest
 */
@JsonPropertyOrder({
  ConstructionFinalizeRequest.JSON_PROPERTY_NETWORK_IDENTIFIER,
  ConstructionFinalizeRequest.JSON_PROPERTY_UNSIGNED_TRANSACTION,
  ConstructionFinalizeRequest.JSON_PROPERTY_SIGNATURE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-11-24T23:25:44.231186-06:00[America/Chicago]")
public class ConstructionFinalizeRequest {
  public static final String JSON_PROPERTY_NETWORK_IDENTIFIER = "network_identifier";
  private NetworkIdentifier networkIdentifier;

  public static final String JSON_PROPERTY_UNSIGNED_TRANSACTION = "unsigned_transaction";
  private String unsignedTransaction;

  public static final String JSON_PROPERTY_SIGNATURE = "signature";
  private Signature signature;


  public ConstructionFinalizeRequest networkIdentifier(NetworkIdentifier networkIdentifier) {
    this.networkIdentifier = networkIdentifier;
    return this;
  }

   /**
   * Get networkIdentifier
   * @return networkIdentifier
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_NETWORK_IDENTIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public NetworkIdentifier getNetworkIdentifier() {
    return networkIdentifier;
  }


  @JsonProperty(JSON_PROPERTY_NETWORK_IDENTIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setNetworkIdentifier(NetworkIdentifier networkIdentifier) {
    this.networkIdentifier = networkIdentifier;
  }


  public ConstructionFinalizeRequest unsignedTransaction(String unsignedTransaction) {
    this.unsignedTransaction = unsignedTransaction;
    return this;
  }

   /**
   * Hex encoded unsigned transaction.
   * @return unsignedTransaction
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Hex encoded unsigned transaction.")
  @JsonProperty(JSON_PROPERTY_UNSIGNED_TRANSACTION)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getUnsignedTransaction() {
    return unsignedTransaction;
  }


  @JsonProperty(JSON_PROPERTY_UNSIGNED_TRANSACTION)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setUnsignedTransaction(String unsignedTransaction) {
    this.unsignedTransaction = unsignedTransaction;
  }


  public ConstructionFinalizeRequest signature(Signature signature) {
    this.signature = signature;
    return this;
  }

   /**
   * Get signature
   * @return signature
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_SIGNATURE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Signature getSignature() {
    return signature;
  }


  @JsonProperty(JSON_PROPERTY_SIGNATURE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setSignature(Signature signature) {
    this.signature = signature;
  }


  /**
   * Return true if this ConstructionFinalizeRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConstructionFinalizeRequest constructionFinalizeRequest = (ConstructionFinalizeRequest) o;
    return Objects.equals(this.networkIdentifier, constructionFinalizeRequest.networkIdentifier) &&
        Objects.equals(this.unsignedTransaction, constructionFinalizeRequest.unsignedTransaction) &&
        Objects.equals(this.signature, constructionFinalizeRequest.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(networkIdentifier, unsignedTransaction, signature);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ConstructionFinalizeRequest {\n");
    sb.append("    networkIdentifier: ").append(toIndentedString(networkIdentifier)).append("\n");
    sb.append("    unsignedTransaction: ").append(toIndentedString(unsignedTransaction)).append("\n");
    sb.append("    signature: ").append(toIndentedString(signature)).append("\n");
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
