.PHONY: output
setup-local:
	pip install pipenv
	pyenv local locust-pers-tests
	pipenv lock -r > requirements.txt
	pip install -r requirements.txt