from locust import main

from locustscript import WalletUser

if __name__ == '__main__':
    from locust.env import Environment

    my_env = Environment(user_classes=[WalletUser])
    WalletUser(my_env).run()