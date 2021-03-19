# See: https://radixdlt.atlassian.net/browse/RPNV1-379
@rpnv1-379
Feature: Staking Queries
  As a Java developer for the Radix network,
  I would like to be able to query staked amounts and status for an account I specify,
  So that I can keep track of staking and staking status.

  #Scenario: 1: Validator without stakes
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1
    #When I request validator stake balance
    #Then I can observe that validator has amount of tokens staked equal to zero

  #Scenario: 2: Staking by one delegator
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1
    #And I stake some tokens by delegator1
    #When I request validator stake balance
    #Then I can observe that validator has amount of tokens staked equal to amount staked by delegator

  #Scenario: 3: Full unstaking by delegator
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1
    #And I stake some tokens by delegator1
    #And I unstake full amount by delegator1
    #When I request validator stake balance
    #Then I can observe that validator has amount of tokens staked equal to zero

  #Scenario: 4: Partial unstaking by delegator
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1
    #And I stake some tokens by delegator1
    #And I unstake partial amount by delegator1
    #When I request validator stake balance
    #Then I can observe that validator has amount of tokens staked equal to initial amount minus unstaked amount

  #Scenario: 5: Staking by different delegators
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1 and delegator2
    #And I stake some tokens by delegator1
    #And I stake some tokens by delegator2
    #When I request validator stake balance
    #Then I can observe that validator has amount of tokens staked equal to sum of stakes by delegator1 and delegator2

  #Scenario: 6: Stake by not configured delegator
    #Given I have access to a suitable Radix network
    #And I have registered validator with allowed delegator1
    #When I try to stake some tokens by delegator2
    #Then I can observe that staking is not allowed
