def call(Map config = [:]) {

    node {

        // =========================
        // ENV SETUP
        // =========================
        def HOME = sh(script: "echo \$HOME", returnStdout: true).trim()
        def PYENV_ROOT = "${HOME}/.pyenv"

        if (!config.repoUrl) {
            error "repoUrl is required"
        }

        def status = "SUCCESS"

        try {

            // =========================
            // CODE CHECKOUT
            // =========================
            stage('Checkout') {
                git branch: config.branch ?: 'main', url: config.repoUrl
            }

            // =========================
            // PYENV + PYTHON 3.11
            // =========================
            stage('Setup Python 3.11 (PyEnv)') {
                sh """
                    export PYENV_ROOT="${PYENV_ROOT}"
                    export PATH="${PYENV_ROOT}/bin:\$PATH"

                    if [ -d "${PYENV_ROOT}" ]; then
                        echo "pyenv exists → skipping install"
                    else
                        curl https://pyenv.run | bash
                    fi

                    eval "\$(pyenv init -)"

                    if ! pyenv versions | grep -q "3.11.8"; then
                        pyenv install 3.11.8
                    fi

                    pyenv global 3.11.8
                    python3.11 --version
                """
            }

            // =========================
            // PIP + POETRY
            // =========================
            stage("Install Pip + Poetry") {
                sh """
                    export PYENV_ROOT="${PYENV_ROOT}"
                    export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"

                    curl -sS https://bootstrap.pypa.io/get-pip.py | python3.11
                    python3.11 -m pip install --upgrade pip
                    python3.11 -m pip install --user poetry

                    which poetry
                    poetry --version
                """
            }

            // =========================
            // DEPENDENCIES INSTALL
            // =========================
            stage('Install Dependencies') {
                sh """
                    export PYENV_ROOT="${PYENV_ROOT}"
                    export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"

                    poetry config virtualenvs.create true
                    poetry config virtualenvs.in-project true

                    poetry install
                """
            }

            // =========================
            // COVERAGE EXECUTION
            // =========================
            stage('Run Code Coverage') {
                sh """
                    export PYENV_ROOT="${PYENV_ROOT}"
                    export PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${HOME}/.local/bin:\$PATH"
                    export PYTHONPATH=\$(pwd)

                    poetry run pytest \
                        --ignore=client/tests/test_postgres_conn.py \
                        --ignore=client/tests/test_redis_conn.py \
                        --cov=. \
                        --cov-report=xml \
                        --cov-report=term
                """
            }

        } catch (err) {
            status = "FAILURE"
            throw err

        } finally {

            def timestamp = sh(script: "TZ=Asia/Kolkata date +'%Y-%m-%d %H:%M:%S'", returnStdout: true).trim()

            def message = """Python Code Coverage - ${status}

Job Name    : ${env.JOB_NAME}
Build No    : #${env.BUILD_NUMBER}
Time (IST)  : ${timestamp}
Build URL   : ${env.BUILD_URL}
"""

            slackSend(
                channel: config.slackChannel ?: "#jenkins-alerts",
                message: message,
                tokenCredentialId: config.slackTokenId ?: "slack-token"
            )

            try {
                mail(
                    to: config.emailRecipients ?: "rule29breaker@gmail.com",
                    subject: "Python Code Coverage - ${status} | Build #${env.BUILD_NUMBER}",
                    body: message
                )
            } catch (e) {
                echo "Email failed, continuing..."
            }
        }
    }
}
