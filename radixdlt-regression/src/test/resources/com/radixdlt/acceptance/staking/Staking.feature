Feature: Staking
  As an account owner,
  I would like to delegate stake to validators,
  And receive token emissions based on the amount staked

  Scenario: 1: Query the total delegated stake of validators
    Given I have an account with funds at a suitable Radix network
    When I request validator information
    Then I observe that validators have stakes delegated to them

  Scenario: 2: Stake tokens
    Given I have an account with funds at a suitable Radix network
    When I stake 95 XRD to a validator
    Then I observe that the validator has 95 XRD more stake
    And I wait for 2 epochs to pass
    Then I receive some emissions

  Scenario: 3: Cannot stake and unstake during the same epoch
    Given I have an account with funds at a suitable Radix network
    When I stake 90 XRD to a validator
    Then I cannot immediately unstake 90 XRD

  #Scenario: 4: Unstake tokens
    #Given I have an account with 120 XRD at a suitable Radix network
    #When I stake 120 XRD to a validator
    #And I wait for the epoch to end
    #And I unstake 120 XRD from a validator
    #Then I observe that my stake is unstaked
    #And I get my tokens back

  #Scenario 5: Stake to a validator which doesn't accept external stake

  #Scenario 6: Stake to full node (non validator)