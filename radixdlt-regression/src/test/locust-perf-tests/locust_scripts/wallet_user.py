from locust import HttpUser, SequentialTaskSet, task, between

from client_api import archive_api
from client_api.dataobjects import RequestData


class UserBehavior(SequentialTaskSet):

    @task()
    def get_transaction_history(self):
        account = archive_api.get_existing_entity(self.client, "accounts")
        payload = RequestData.get_transaction_history()
        payload["params"]["address"] = account
        archive_api.get_transaction_history(self.client, payload,f"history_{account}")



