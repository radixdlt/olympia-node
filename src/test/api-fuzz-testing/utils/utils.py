import random
import string, os


def getID():
    return ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(8))


def get_server_endpoint() -> str:
    if os.getenv('END_POINT'):
        return os.getenv('END_POINT')
        # server_end_point = "3.10.234.150:8080"
    else:
        return "localhost:8080"


def get_max_threads() -> int:
    if os.getenv('MAX_THREADS'):
        return int(os.getenv('MAX_THREADS'))
        # server_end_point = "3.10.234.150:8080"
    else:
        return 200
