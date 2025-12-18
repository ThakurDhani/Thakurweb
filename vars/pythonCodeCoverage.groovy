def call(Map config = [:]) {

    node {

        def repoUrl     = config.repoUrl
        def branch      = config.branch ?: 'main'
        def projectName = config.projectName ?: 'Python Code Coverage'

        if (!repoUrl) {
            error "repoUrl is required"
        }

        def status = 'SUCCESS'

        try {

            stage('Checkout') {
                git branch: branch, url: repoUrl
            }

            stage('Setup Python 3.11 (PyEnv)') {
                sh '''
                    # download pyenv if not present
                    if [ ! -d "$HOME/.pyenv" ]; then
                        curl https://pyenv.run | bash
                    fi

                    export PYENV_ROOT="$HOME/.pyenv"
                    export PATH="$PYENV_ROOT/bin:$PATH"

                    eval "$(pyenv init -)"

                    # install python 3.11 if not present
                    if ! pyenv versions | grep -q "3.11"; then
                        pyenv install 3.11.8
                    fi

                    pyenv global 3.11.8

                    python3.11 --version
                '''
            }

            stage('Install Pip + Poetry') {
                sh '''
                    export PYENV_ROOT="$HOME/.pyenv"
                    export PATH="$PYENV_ROOT/shims:$PATH"

                    curl -sS https://bootstrap.pypa.io/get-pip.py | python3.11
                    python3.11 -m pip install --upgrade pip
                    python3.11 -m pip install poetry

                    poetry --version
                '''
            }

            stage('Install Dependencies') {
                sh '''
                    export PYENV_ROOT="$HOME/.pyenv"
                    export PATH="$PYENV_ROOT/shims:$PATH"

                    poetry config virtualenvs.create true
                    poetry config virtualenvs.in-project true
                    poetry install --no-root
                '''
            }

            stage('Run Code Coverage') {
                sh '''
                    export PYENV_ROOT="$HOME/.pyenv"
                    export PATH="$PYENV_ROOT/shims:$PATH"
                    export PYTHONPATH=$(pwd)

                    poetry run pytest \
                        --ignore=client/tests/test_postgres_conn.py \
                        --ignore=client/tests/test_redis_conn.py \
                        --cov=. \
                        --cov-report=term \
                        --cov-report=xml
                '''
            }

        } catch (err) {
            status = 'FAILURE'
            throw err

        } finally {

            def timestamp = sh(
                script: "TZ=Asia/Kolkata date +'%Y-%m-%d %H:%M:%S'",
                returnStdout: true
            ).trim()

            def message = """${projectName} - ${status}

Job Name    : ${env.JOB_NAME}
Build No    : #${env.BUILD_NUMBER}
Triggered By: Jenkins
Time (IST)  : ${timestamp}

Build URL:
${env.BUILD_URL}
"""

            slackSend(
                channel: config.slackChannel ?: '#jenkins-alerts',
                message: message,
                tokenCredentialId: config.slackTokenId ?: 'slack-token'
            )

            try {
                mail(
                    to: config.emailRecipients ?: 'rule29breaker@gmail.com',
                    subject: "${projectName} - ${status} | Build #${env.BUILD_NUMBER}",
                    body: message
                )
            } catch (e) {
                echo "Email notification failed, continuing pipeline"
            }
        }
    }
}
