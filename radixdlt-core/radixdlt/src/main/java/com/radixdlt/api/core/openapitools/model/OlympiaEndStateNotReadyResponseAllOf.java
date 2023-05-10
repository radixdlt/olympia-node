/*
 * Radix Core API
 * This API provides endpoints from a node for integration with the Radix ledger.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > designed to be accessed in a private network.  The Core API is separated into three: * The **Data API** is a read-only api which allows you to view and sync to the state of the ledger. * The **Construction API** allows you to construct and submit a transaction to the network. * The **Key API** allows you to use the keys managed by the node to sign transactions.  The Core API is a low level API primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  For a higher level API, see the [Gateway API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt-network-gateway/main/generation/gateway-api-spec.yaml).  For node monitoring, see the [System API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt/main/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/system/api.yaml).  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Enabling Endpoints  All endpoints are enabled when running a node with the exception of two endpoints, each of which need to be manually configured to access: * `/transactions` endpoint must be enabled with configuration `api.transaction.enable=true`. This is because the transactions endpoint requires additional database storage which may not be needed for users who aren't using this endpoint * `/key/sign` endpoint must be enable with configuration `api.sign.enable=true`. This is a potentially dangerous endpoint if accessible publicly so it must be enabled manually.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/main/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/api.yaml). See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  The Data API can be used to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/update-documentation/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  The Construction API can be used to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release. 
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.core.openapitools.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * OlympiaEndStateNotReadyResponseAllOf
 */
@JsonPropertyOrder({
  OlympiaEndStateNotReadyResponseAllOf.JSON_PROPERTY_TEST_PAYLOAD,
  OlympiaEndStateNotReadyResponseAllOf.JSON_PROPERTY_TEST_PAYLOAD_HASH,
  OlympiaEndStateNotReadyResponseAllOf.JSON_PROPERTY_SIGNATURE
})
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-05-10T18:05:13.417843654+02:00[Europe/Warsaw]")
public class OlympiaEndStateNotReadyResponseAllOf {
  public static final String JSON_PROPERTY_TEST_PAYLOAD = "test_payload";
  private String testPayload;

  public static final String JSON_PROPERTY_TEST_PAYLOAD_HASH = "test_payload_hash";
  private String testPayloadHash;

  public static final String JSON_PROPERTY_SIGNATURE = "signature";
  private String signature;

  public OlympiaEndStateNotReadyResponseAllOf() { 
  }

  public OlympiaEndStateNotReadyResponseAllOf testPayload(String testPayload) {
    this.testPayload = testPayload;
    return this;
  }

   /**
   * A base64-encoded test payload
   * @return testPayload
  **/
  @javax.annotation.Nullable
  @JsonProperty(JSON_PROPERTY_TEST_PAYLOAD)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getTestPayload() {
    return testPayload;
  }


  @JsonProperty(JSON_PROPERTY_TEST_PAYLOAD)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setTestPayload(String testPayload) {
    this.testPayload = testPayload;
  }


  public OlympiaEndStateNotReadyResponseAllOf testPayloadHash(String testPayloadHash) {
    this.testPayloadHash = testPayloadHash;
    return this;
  }

   /**
   * A hex-encoded hash of the test_payload
   * @return testPayloadHash
  **/
  @javax.annotation.Nullable
  @JsonProperty(JSON_PROPERTY_TEST_PAYLOAD_HASH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getTestPayloadHash() {
    return testPayloadHash;
  }


  @JsonProperty(JSON_PROPERTY_TEST_PAYLOAD_HASH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setTestPayloadHash(String testPayloadHash) {
    this.testPayloadHash = testPayloadHash;
  }


  public OlympiaEndStateNotReadyResponseAllOf signature(String signature) {
    this.signature = signature;
    return this;
  }

   /**
   * The hex-encoded DER signature of the test_payload_hash, signed with the node&#39;s key
   * @return signature
  **/
  @javax.annotation.Nullable
  @JsonProperty(JSON_PROPERTY_SIGNATURE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getSignature() {
    return signature;
  }


  @JsonProperty(JSON_PROPERTY_SIGNATURE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setSignature(String signature) {
    this.signature = signature;
  }


  /**
   * Return true if this OlympiaEndStateNotReadyResponse_allOf object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OlympiaEndStateNotReadyResponseAllOf olympiaEndStateNotReadyResponseAllOf = (OlympiaEndStateNotReadyResponseAllOf) o;
    return Objects.equals(this.testPayload, olympiaEndStateNotReadyResponseAllOf.testPayload) &&
        Objects.equals(this.testPayloadHash, olympiaEndStateNotReadyResponseAllOf.testPayloadHash) &&
        Objects.equals(this.signature, olympiaEndStateNotReadyResponseAllOf.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(testPayload, testPayloadHash, signature);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OlympiaEndStateNotReadyResponseAllOf {\n");
    sb.append("    testPayload: ").append(toIndentedString(testPayload)).append("\n");
    sb.append("    testPayloadHash: ").append(toIndentedString(testPayloadHash)).append("\n");
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

  /**
   * Convert the instance into URL query string.
   *
   * @return URL query string
   */
  public String toUrlQueryString() {
    return toUrlQueryString(null);
  }

  /**
   * Convert the instance into URL query string.
   *
   * @param prefix prefix of the query string
   * @return URL query string
   */
  public String toUrlQueryString(String prefix) {
    String suffix = "";
    String containerSuffix = "";
    String containerPrefix = "";
    if (prefix == null) {
      // style=form, explode=true, e.g. /pet?name=cat&type=manx
      prefix = "";
    } else {
      // deepObject style e.g. /pet?id[name]=cat&id[type]=manx
      prefix = prefix + "[";
      suffix = "]";
      containerSuffix = "]";
      containerPrefix = "[";
    }

    StringJoiner joiner = new StringJoiner("&");

    // add `test_payload` to the URL query string
    if (getTestPayload() != null) {
      joiner.add(String.format("%stest_payload%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getTestPayload()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `test_payload_hash` to the URL query string
    if (getTestPayloadHash() != null) {
      joiner.add(String.format("%stest_payload_hash%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getTestPayloadHash()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `signature` to the URL query string
    if (getSignature() != null) {
      joiner.add(String.format("%ssignature%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getSignature()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    return joiner.toString();
  }
}

