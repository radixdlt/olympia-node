# DefaultApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**accountBalancesPost**](DefaultApi.md#accountBalancesPost) | **POST** /account/balances | Get Token Balances
[**accountStakesPost**](DefaultApi.md#accountStakesPost) | **POST** /account/stakes | Get Stake Positions
[**accountTransactionsPost**](DefaultApi.md#accountTransactionsPost) | **POST** /account/transactions | Get Account Transactions
[**accountUnstakesPost**](DefaultApi.md#accountUnstakesPost) | **POST** /account/unstakes | Get Unstake Positions
[**constructionBuildPost**](DefaultApi.md#constructionBuildPost) | **POST** /construction/build | Build Transaction
[**constructionFinalizePost**](DefaultApi.md#constructionFinalizePost) | **POST** /construction/finalize | Finalize Transaction
[**constructionSubmitPost**](DefaultApi.md#constructionSubmitPost) | **POST** /construction/submit | Submit Transaction
[**networkPost**](DefaultApi.md#networkPost) | **POST** /network | Get Network
[**tokenPost**](DefaultApi.md#tokenPost) | **POST** /token | Get Token Info
[**transactionPost**](DefaultApi.md#transactionPost) | **POST** /transaction | Get Transaction Info
[**transactionStatusPost**](DefaultApi.md#transactionStatusPost) | **POST** /transaction/status | Get Transaction Status
[**validatorPost**](DefaultApi.md#validatorPost) | **POST** /validator | Get Validator Info
[**validatorsPost**](DefaultApi.md#validatorsPost) | **POST** /validators | Get Validators


<a name="accountBalancesPost"></a>
# **accountBalancesPost**
> AccountBalancesResponse accountBalancesPost(accountBalancesRequest)

Get Token Balances

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    AccountBalancesRequest accountBalancesRequest = new AccountBalancesRequest(); // AccountBalancesRequest | 
    try {
      AccountBalancesResponse result = apiInstance.accountBalancesPost(accountBalancesRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#accountBalancesPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountBalancesRequest** | [**AccountBalancesRequest**](AccountBalancesRequest.md)|  |

### Return type

[**AccountBalancesResponse**](AccountBalancesResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | List of token balances |  -  |
**500** | Unexpected error |  -  |

<a name="accountStakesPost"></a>
# **accountStakesPost**
> AccountStakesResponse accountStakesPost(accountStakesRequest)

Get Stake Positions

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    AccountStakesRequest accountStakesRequest = new AccountStakesRequest(); // AccountStakesRequest | 
    try {
      AccountStakesResponse result = apiInstance.accountStakesPost(accountStakesRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#accountStakesPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountStakesRequest** | [**AccountStakesRequest**](AccountStakesRequest.md)|  |

### Return type

[**AccountStakesResponse**](AccountStakesResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | List of Stake Positions |  -  |
**500** | Unexpected error |  -  |

<a name="accountTransactionsPost"></a>
# **accountTransactionsPost**
> AccountTransactionsResponse accountTransactionsPost(accountTransactionsRequest)

Get Account Transactions

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    AccountTransactionsRequest accountTransactionsRequest = new AccountTransactionsRequest(); // AccountTransactionsRequest | 
    try {
      AccountTransactionsResponse result = apiInstance.accountTransactionsPost(accountTransactionsRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#accountTransactionsPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountTransactionsRequest** | [**AccountTransactionsRequest**](AccountTransactionsRequest.md)|  |

### Return type

[**AccountTransactionsResponse**](AccountTransactionsResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | List of Transactions |  -  |
**500** | Unexpected error |  -  |

<a name="accountUnstakesPost"></a>
# **accountUnstakesPost**
> AccountUnstakesResponse accountUnstakesPost(accountUnstakesRequest)

Get Unstake Positions

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    AccountUnstakesRequest accountUnstakesRequest = new AccountUnstakesRequest(); // AccountUnstakesRequest | 
    try {
      AccountUnstakesResponse result = apiInstance.accountUnstakesPost(accountUnstakesRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#accountUnstakesPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountUnstakesRequest** | [**AccountUnstakesRequest**](AccountUnstakesRequest.md)|  |

### Return type

[**AccountUnstakesResponse**](AccountUnstakesResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | List of Unstake Positions |  -  |
**500** | Unexpected error |  -  |

<a name="constructionBuildPost"></a>
# **constructionBuildPost**
> ConstructionBuildResponse constructionBuildPost(constructionBuildRequest)

Build Transaction

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    ConstructionBuildRequest constructionBuildRequest = new ConstructionBuildRequest(); // ConstructionBuildRequest | 
    try {
      ConstructionBuildResponse result = apiInstance.constructionBuildPost(constructionBuildRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#constructionBuildPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **constructionBuildRequest** | [**ConstructionBuildRequest**](ConstructionBuildRequest.md)|  |

### Return type

[**ConstructionBuildResponse**](ConstructionBuildResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | An unsigned transaction |  -  |
**500** | Unexpected error |  -  |

<a name="constructionFinalizePost"></a>
# **constructionFinalizePost**
> ConstructionFinalizeResponse constructionFinalizePost(constructionFinalizeRequest)

Finalize Transaction

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    ConstructionFinalizeRequest constructionFinalizeRequest = new ConstructionFinalizeRequest(); // ConstructionFinalizeRequest | 
    try {
      ConstructionFinalizeResponse result = apiInstance.constructionFinalizePost(constructionFinalizeRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#constructionFinalizePost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **constructionFinalizeRequest** | [**ConstructionFinalizeRequest**](ConstructionFinalizeRequest.md)|  |

### Return type

[**ConstructionFinalizeResponse**](ConstructionFinalizeResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Final Signed Transaction |  -  |
**500** | Unexpected error |  -  |

<a name="constructionSubmitPost"></a>
# **constructionSubmitPost**
> ConstructionSubmitResponse constructionSubmitPost(constructionSubmitRequest)

Submit Transaction

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    ConstructionSubmitRequest constructionSubmitRequest = new ConstructionSubmitRequest(); // ConstructionSubmitRequest | 
    try {
      ConstructionSubmitResponse result = apiInstance.constructionSubmitPost(constructionSubmitRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#constructionSubmitPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **constructionSubmitRequest** | [**ConstructionSubmitRequest**](ConstructionSubmitRequest.md)|  |

### Return type

[**ConstructionSubmitResponse**](ConstructionSubmitResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Successful Submission |  -  |
**500** | Unexpected error |  -  |

<a name="networkPost"></a>
# **networkPost**
> NetworkResponse networkPost(body)

Get Network

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    Object body = null; // Object | 
    try {
      NetworkResponse result = apiInstance.networkPost(body);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#networkPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **body** | **Object**|  |

### Return type

[**NetworkResponse**](NetworkResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | The Network |  -  |
**500** | Unexpected error |  -  |

<a name="tokenPost"></a>
# **tokenPost**
> TokenInfoResponse tokenPost(tokenInfoRequest)

Get Token Info

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    TokenInfoRequest tokenInfoRequest = new TokenInfoRequest(); // TokenInfoRequest | 
    try {
      TokenInfoResponse result = apiInstance.tokenPost(tokenInfoRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#tokenPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **tokenInfoRequest** | [**TokenInfoRequest**](TokenInfoRequest.md)|  |

### Return type

[**TokenInfoResponse**](TokenInfoResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Token info |  -  |
**500** | Unexpected error |  -  |

<a name="transactionPost"></a>
# **transactionPost**
> TransactionInfoResponse transactionPost(transactionInfoRequest)

Get Transaction Info

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    TransactionInfoRequest transactionInfoRequest = new TransactionInfoRequest(); // TransactionInfoRequest | 
    try {
      TransactionInfoResponse result = apiInstance.transactionPost(transactionInfoRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#transactionPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **transactionInfoRequest** | [**TransactionInfoRequest**](TransactionInfoRequest.md)|  |

### Return type

[**TransactionInfoResponse**](TransactionInfoResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Transaction info |  -  |
**500** | Unexpected error |  -  |

<a name="transactionStatusPost"></a>
# **transactionStatusPost**
> TransactionStatusResponse transactionStatusPost(transactionStatusRequest)

Get Transaction Status

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    TransactionStatusRequest transactionStatusRequest = new TransactionStatusRequest(); // TransactionStatusRequest | 
    try {
      TransactionStatusResponse result = apiInstance.transactionStatusPost(transactionStatusRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#transactionStatusPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **transactionStatusRequest** | [**TransactionStatusRequest**](TransactionStatusRequest.md)|  |

### Return type

[**TransactionStatusResponse**](TransactionStatusResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Transaction Status |  -  |
**500** | Unexpected error |  -  |

<a name="validatorPost"></a>
# **validatorPost**
> ValidatorInfoResponse validatorPost(validatorInfoRequest)

Get Validator Info

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    ValidatorInfoRequest validatorInfoRequest = new ValidatorInfoRequest(); // ValidatorInfoRequest | 
    try {
      ValidatorInfoResponse result = apiInstance.validatorPost(validatorInfoRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#validatorPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **validatorInfoRequest** | [**ValidatorInfoRequest**](ValidatorInfoRequest.md)|  |

### Return type

[**ValidatorInfoResponse**](ValidatorInfoResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Validator Info |  -  |
**500** | Unexpected error |  -  |

<a name="validatorsPost"></a>
# **validatorsPost**
> ValidatorsResponse validatorsPost(validatorsRequest)

Get Validators

### Example
```java
// Import classes:
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.models.*;
import org.openapitools.client.api.DefaultApi;

public class Example {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    DefaultApi apiInstance = new DefaultApi(defaultClient);
    ValidatorsRequest validatorsRequest = new ValidatorsRequest(); // ValidatorsRequest | 
    try {
      ValidatorsResponse result = apiInstance.validatorsPost(validatorsRequest);
      System.out.println(result);
    } catch (ApiException e) {
      System.err.println("Exception when calling DefaultApi#validatorsPost");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      e.printStackTrace();
    }
  }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **validatorsRequest** | [**ValidatorsRequest**](ValidatorsRequest.md)|  |

### Return type

[**ValidatorsResponse**](ValidatorsResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | Ordered list of validators |  -  |
**500** | Unexpected error |  -  |

