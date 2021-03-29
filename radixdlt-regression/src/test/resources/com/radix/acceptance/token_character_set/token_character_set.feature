# See: https://radixdlt.atlassian.net/browse/RLAU-438
@rlau-438
Feature: Restricted character set for token symbols
  As a token trader,
  I would like token symbols to be restricted to a well-known character set,
  So that token symbols are not easily confused

  The definition of "well-known" in this context is defined as the alphabetic
  characters "ABCDEFGHIJKLMNOPQRSTUVWXYZ" and the numeric characters "0123456789".

#Scenario: 1: Token symbol has bad characters
  #Given I have access to a suitable Radix network
  #When I submit a mutable-supply token-creation request with symbol "ABC DEF"
  #Then I can observe the atom being rejected with a failure

#Scenario: 2: Token symbol has bad characters
  #Given I have access to a suitable Radix network
  #When I submit a mutable-supply token-creation request with symbol "ABCcDEF"
  #Then I can observe the atom being rejected with a validation error

#Scenario: 3: Token symbol has bad characters
  #Given I have access to a suitable Radix network
  #When I submit a mutable-supply token-creation request with symbol "123+DEF"
  #Then I can observe the atom being rejected with a failure

#Scenario: 4: Token symbol is OK
  #Given I have access to a suitable Radix network
  #When I submit a mutable-supply token-creation request with symbol "123DEF0"
  #Then I can observe the atom being accepted

#Scenario: 5: Token symbol is OK
  #Given I have access to a suitable Radix network
  #When I submit a mutable-supply token-creation request with symbol "123Z"
  #Then I can observe the atom being accepted
