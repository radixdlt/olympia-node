/*
 * Wallet/Explorer Api
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 2.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package org.openapitools.client.model;

import java.util.Objects;
import java.util.Arrays;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import org.openapitools.client.model.ConstructionBuildError;
import org.openapitools.client.model.ConstructionBuildErrorResponse;
import org.openapitools.client.model.ConstructionBuildErrorResponseAllOf;
import org.openapitools.client.model.ConstructionBuildResponse;
import org.openapitools.client.model.ConstructionBuildSuccessResponse;

/**
 * ConstructionBuildErrorResponse
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-10-25T08:11:19.875906-07:00[America/Los_Angeles]")
public class ConstructionBuildErrorResponse extends ConstructionBuildResponse {
  public static final String SERIALIZED_NAME_DETAILS = "details";
  @SerializedName(SERIALIZED_NAME_DETAILS)
  private ConstructionBuildError details;

  public ConstructionBuildErrorResponse() {
    this.result = this.getClass().getSimpleName();
  }

  public ConstructionBuildErrorResponse details(ConstructionBuildError details) {
    
    this.details = details;
    return this;
  }

   /**
   * Get details
   * @return details
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")

  public ConstructionBuildError getDetails() {
    return details;
  }


  public void setDetails(ConstructionBuildError details) {
    this.details = details;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConstructionBuildErrorResponse constructionBuildErrorResponse = (ConstructionBuildErrorResponse) o;
    return Objects.equals(this.details, constructionBuildErrorResponse.details) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(details, super.hashCode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ConstructionBuildErrorResponse {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    details: ").append(toIndentedString(details)).append("\n");
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

