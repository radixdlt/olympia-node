.PHONY: output
setup-local:
	$(shell chmod +x add-version.sh)
	$(shell ./add-version.sh)
	pip install pipenv
	pyenv local locust-pers-tests
	pipenv lock -r > requirements.txt
	pip install -r requirements.txt