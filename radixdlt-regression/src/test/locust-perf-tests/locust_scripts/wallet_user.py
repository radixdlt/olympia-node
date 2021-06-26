from locust import HttpUser, SequentialTaskSet, task, between

from client_api import archive_api
from client_api.dataobjects import RequestData


class UserBehavior(SequentialTaskSet):

    @task()
    def get_transaction_history(self):
        account = archive_api.get_existing_entity(self.client, "accounts")
        payload = RequestData.get_transaction_history()
        payload["params"]["address"] = str(account)
        archive_api.get_transaction_history(self.client, payload, f"transaction_history")

    @task()
    def stake_tokens(self):
        from_account = archive_api.get_existing_entity(self.client, "accounts")
        to_validator = archive_api.get_existing_entity(self.client, "validators")
        payload = RequestData.build_transaction()
        action_stake_transaction = {
            "type": "StakeTokens",
            "from": str(from_account),
            "validator": str(to_validator),
            "amount": "11000000000000000000"
        }
        payload["params"]["actions"].append(action_stake_transaction)
        payload["params"]["feePayer"] = str(from_account)
        archive_api.build_stake_transactions(self.client, payload, "build_stake_tokens")
