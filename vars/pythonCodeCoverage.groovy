def call(Map config = [:]) {

    node {

        def HOME = sh(script: "echo \$HOME", returnStdout: true).trim()
        def PYENV_ROOT = "${HOME}/.pyenv"

        stage('Checkout') {
            git branch: config.branch ?: 'main', url: config.repoUrl
        }

        stage('Setup Python 3.11 (PyEnv)') {
            sh """
                # define variables properly
                export PYENV_ROOT="${PYENV_ROOT}"
                export PATH="${PYENV_ROOT}/bin:\$PATH"

                # if pyenv exists, skip install
                if [ -d "${PYENV_ROOT}" ]; then
                    echo "pyenv exists → skipping install"
                else
                    curl https://pyenv.run | bash
                fi

                # activate pyenv
                eval "\$(pyenv init -)"

                # install python 3.11 only if missing
                if ! pyenv versions | grep -q "3.11.8"; then
                    pyenv install 3.11.8
                else
                    echo "Python 3.11.8 exists → skipping install"
                fi

                pyenv global 3.11.8
                python3.11 --version
            """
        }

        stage('Install Pip + Poetry') {
            sh """
                export PYENV_ROOT="${PYENV_ROOT}"
                export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"

                curl -sS https://bootstrap.pypa.io/get-pip.py | python3.11
                python3.11 -m pip install --user poetry

                poetry --version
            """
        }

        stage('Install Dependencies') {
            sh """
                export PYENV_ROOT="${PYENV_ROOT}"
                export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"

                poetry config virtualenvs.create true
                poetry config virtualenvs.in-project true
                poetry install --no-root
            """
        }

        stage('Run Code Coverage') {
            sh """
                export PYENV_ROOT="${PYENV_ROOT}"
                export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"

                poetry run pytest --cov=. --cov-report=xml --cov-report=term
            """
        }
    }
}
