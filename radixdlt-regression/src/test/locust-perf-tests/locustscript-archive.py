from locust import HttpUser, between
import resource
from locust_scripts.archive_user import ArchiveBehavior


class ArchiveUser(HttpUser):
    resource.setrlimit(resource.RLIMIT_NOFILE, (999999, 999999))
    # host = "https://releasenet.radixdlt.com"
    host = "https://54.146.247.180"
    tasks = [ArchiveBehavior]
    wait_time = between(1, 3)