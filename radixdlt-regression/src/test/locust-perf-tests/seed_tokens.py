import time

from client_api.archive_api import get_existing_entity, transfer_tokens_method, data, get_transaction_history_method
from client_api.datapool import DataPool

from locust import HttpUser, SequentialTaskSet, task, between


class SeedToken(SequentialTaskSet):
    @task()
    def seed_token(self):
        testData = DataPool(data)
        from_account = testData["source_account"][0]
        for account in testData["accounts"]:
            print(f"Tranferring tokens wallet {from_account['wallet_address']} to  {account['wallet_address']}")
            transfer_tokens_method(self.client, from_account, account)
            get_transaction_history_method(self.client,from_account)
            time.sleep(10)




class SuperUser(HttpUser):
    host = "https://releasenet.radixdlt.com"
    tasks = [SeedToken]
    wait_time = between(1, 3)


if __name__ == '__main__':
    from locust.env import Environment

    my_env = Environment(user_classes=[SuperUser])
    SuperUser(my_env).run()
