Feature: Mutable Supply Tokens
  As an account owner
  I want to create a mutable supply token in my account with some key properties of an ERC-20 token
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  #Scenario: 1: Creating a unique token
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with name "RLAU Test", symbol "RLAU", initialSupply 1000000000 and granularity 1
    #Then I can observe the atom being accepted

  #Scenario: 1a: Creating a unique token with blank name
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with name "", symbol "RLAU", initialSupply 1000000000 and granularity 1
    #Then I can observe the atom being accepted

  #Scenario: 1b: Creating a unique token with blank description
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with name "", description "", symbol "RLAU", initialSupply 1000000000 and granularity 1
    #Then I can observe the atom being accepted

  #Scenario: 2: Creating a conflicting token
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU"
    #And I observe the atom being accepted
    #And I submit a mutable-supply token-creation request with symbol "RLAU"
    #Then I can observe atom 2 being rejected with an error

  #Scenario: 3: Creating a token with disallowed granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with granularity 0
    #Then I can observe the atom being rejected with a validation error

  #Scenario: 4: Transacting within granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a token transfer request of 100 for "RLAU" to an arbitrary account
    #Then I can observe atom 2 being accepted

  #Scenario: 5: Transacting outside granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a token transfer request of 1 for "RLAU" to an arbitrary account
    #Then I can observe atom 2 being rejected with a validation error

  #Scenario: 6: Minting within granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a mint request of 100 for "RLAU"
    #Then I can observe atom 2 being accepted

  #Scenario: 7: Minting outside granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a mint request of 1 for "RLAU"
    #Then I can observe atom 2 being rejected with a validation error

  #Scenario: 8: Burning within granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a burn request of 100 for "RLAU"
    #Then I can observe atom 2 being accepted

  #Scenario: 9: Burning outside granularity
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and granularity 2
    #And I observe the atom being accepted
    #And I submit a burn request of 1 for "RLAU"
    #Then I can observe atom 2 being rejected with a validation error

  #Scenario: 10: Initial supply
    #Given I have access to a suitable Radix network
    #When I submit a mutable-supply token-creation request with symbol "RLAU" and initialSupply 1000
    #Then I can observe the atom being accepted
    #And I can observe token "RLAU" balance equal to 1000
