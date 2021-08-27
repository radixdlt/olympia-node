from locust import HttpUser, SequentialTaskSet, task, between

from client_api import archive_api
from client_api.archive_api import get_archive_network_id_method, get_native_token_method, \
    get_relasenet_transaction_history_method, get_lookup_validator_method


class ArchiveBehavior(SequentialTaskSet):
    @task()
    def get_native_token(self):
        get_native_token_method(self.client)

    @task()
    def get_archive_network_id(self):
        get_archive_network_id_method(self.client)

    # @task()
    # def get_account_balance(self):
    #     get_account_balance_method(self.client)

    @task()
    def get_transaction_history(self):
        get_relasenet_transaction_history_method(self.client)

    @task()
    def get_lookup_validator(self):
        get_lookup_validator_method(self.client)

#     @task()
#     def stake_tokens(self):
#         from_account = get_existing_entity("accounts")
#         to_validator = get_existing_entity("validators")
#         stake_tokens_method(self.client, from_account, to_validator)
#
#     @task()
#     def transfer_token(self):
#         from_account = get_existing_entity("accounts")
#
#         while True:
#             to_account = get_existing_entity("accounts")
#             if from_account["private_key"] != to_account["private_key"]:
#                 break
#
#         transfer_tokens_method(self.client, from_account, to_account)
