Feature: Fixed Supply Tokens
  As an account owner
  I want to create a fixed supply token with some key properties of an ERC-20 token,
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  #Scenario: 1: Creating a unique token
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with name "RLAU Test", symbol "RLAU", totalSupply 100000000 scaled and granularity 1 scaled
    #Then I can observe the atom being accepted

  #Scenario: 1a: Creating a unique token with an empty name
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with name "", symbol "RLAU", totalSupply 100000000 scaled and granularity 1 scaled
    #Then I can observe the atom being accepted

  #Scenario: 1b: Creating a unique token with an empty description
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with name "RLAU Test", description "", symbol "RLAU", totalSupply 100000000 scaled and granularity 1 scaled
    #Then I can observe the atom being accepted

  #Scenario: 2: Creating a conflicting token
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with symbol "RLAU"
    #And I observe the atom being accepted
    #And I submit a fixed-supply token-creation request with symbol "RLAU"
    #Then I can observe atom 2 being rejected with an error

  #Scenario: 3: Creating a token with disallowed granularity
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with granularity 0
    #Then I can observe the atom being rejected with a validation error

  #Scenario: 4: Transacting within granularity
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with granularity 2 scaled
    #And I observe the atom being accepted
    #And I submit a token transfer request of 100 scaled for "RLAU" to an arbitrary account
    #Then I can observe atom 2 being accepted

  #Scenario: 5: Transacting outside granularity
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with granularity 2 scaled
    #And I observe the atom being accepted
    #And I submit a token transfer request of 1 scaled for "RLAU" to an arbitrary account
    #Then I can observe atom 2 being rejected with a validation error

  #Scenario: 6: Total supply
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with symbol "RLAU" and totalSupply 1000 scaled
    #Then I can observe the atom being accepted
    #And I can observe token "RLAU" balance equal to 1000 scaled

  #Scenario: 7: Granularity off
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with granularity 3 scaled
    #Then I can observe the atom being rejected with a validation error

