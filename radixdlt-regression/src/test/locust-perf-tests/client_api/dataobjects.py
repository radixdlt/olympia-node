class RequestData():
    @staticmethod
    def get_transaction_history():
        return {
            "jsonrpc": "2.0",
            "id": "0",
            "method": "account.get_transaction_history",
            "params":
                {"address": "brx1qspm44wuhymwwse4jg8y4y8nkj657e3g4659hw53cgg59mkp74w85hqyapytf",
                 "size": 50}
        }

    @staticmethod
    def build_transaction():
        return {
            "method": "construction.build_transaction",
            "params": {
                "actions": [

                ],
                "feePayer": ""
            },
            "id": 0
        }
