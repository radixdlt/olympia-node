from locust import HttpUser, SequentialTaskSet, task, between

from client_api import archive_api
from client_api.archive_api import stake_tokens_method, get_existing_entity, transfer_tokens_method, \
    get_transaction_history_method
from client_api.dataobjects import RequestData
from radix_transaction.signatory import Signatory


class UserBehavior(SequentialTaskSet):

    @task()
    def get_transaction_history(self):
        account = archive_api.get_existing_entity("accounts")
        get_transaction_history_method(self.client, account)

    @task()
    def stake_tokens(self):
        from_account = get_existing_entity("accounts")
        to_validator = get_existing_entity("validators")
        stake_tokens_method(self.client, from_account, to_validator)

    @task()
    def transfer_token(self):
        from_account = get_existing_entity("accounts")

        while True:
            to_account = get_existing_entity("accounts")
            if from_account["private_key"] != to_account["private_key"]:
                break

        transfer_tokens_method(self.client, from_account, to_account)
