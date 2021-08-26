from locust import HttpUser, between

from locust_scripts.archive_user import ArchiveBehavior


class ArchiveUser(HttpUser):
    host = "https://releasenet.radixdlt.com"
    tasks = [ArchiveBehavior]
    wait_time = between(1, 3)