# See: https://radixdlt.atlassian.net/browse/RLAU-434
@rlau-434
Feature: Token symbol lengths
  As a token creator,
  I would like to use token symbols between 1 and 14 characters in length,
  So that I can mimic the real world

  #Scenario: 1: Token symbol too short
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol ""
    #Then I can observe the atom being rejected with a failure

  #Scenario: 2: Token symbol too long
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "ABCDEFGHIJKLMNO"
    #Then I can observe the atom being rejected with a validation error

  #Scenario: 3: Token symbol OK
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "ABCDEFGHIJKLMN"
    #Then I can observe the atom being accepted
