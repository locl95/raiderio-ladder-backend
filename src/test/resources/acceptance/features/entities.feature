Feature: Entities

  Background:
    Given "sanxei" exists in the database with role "admin"
    And "sanxei" has a valid token with activities "search entity, check entities exist"

  Scenario: Searching for an unknown WOW entity queues it for sync
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response data is null
    And the response contains an operation
    And a sync event exists for "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"

  Scenario: Searching for a WOW entity with cached data returns the data
    Given a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowEntity"
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response data is a valid WOW entity
    And the operation is null

  Scenario: Searching for an unknown LOL entity queues it for sync
    When they search for a "LOL" entity "GTP ZeroMVPs" with tag "EUW"
    Then the response status is 200
    And the response data is null
    And the response contains an operation

  Scenario: Searching for a LOL entity with cached data returns the data
    Given a LOL entity "GTP ZeroMVPs" with tag "EUW" exists with cached data "lolEntity"
    When they search for a "LOL" entity "GTP ZeroMVPs" with tag "EUW"
    Then the response status is 200
    And the response data is a valid LOL entity
    And the operation is null

  Scenario: Checking existence of a batch of WOW entities splits them into exist, nonExisting and unchecked
    When they check existence of "WOW" entities:
      | name              | realm      | region |
      | Sanxei            | Silvermoon | eu     |
      | NonExistentEntity | Silvermoon | eu     |
      | UncheckedEntity   | Silvermoon | eu     |
    Then the response status is 200
    And "Sanxei" is in the "exist" bucket
    And "NonExistentEntity" is in the "nonExisting" bucket
    And "UncheckedEntity" is in the "unchecked" bucket

  Scenario: Checking existence of a WOW guild returns its payload when it exists
    Given a WOW guild roster is available from the Blizzard API
    When they check existence of guild "Method" on realm "twisting-nether" region "eu"
    Then the response status is 200
    And the guild exists in the response
