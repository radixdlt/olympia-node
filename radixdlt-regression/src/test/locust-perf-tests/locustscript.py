from locust import HttpUser, between

from locust_scripts.wallet_user import UserBehavior


class WalletUser(HttpUser):
    host = "https://releasenet.radixdlt.com"
    tasks = [UserBehavior]
    wait_time = between(1, 3)