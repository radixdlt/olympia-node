class DataPool:

    def __init__(self, data):
        self.__dict__ = data

    def __getitem__(self, key):
        return getattr(self, key)
