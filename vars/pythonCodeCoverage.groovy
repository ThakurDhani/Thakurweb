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

            stage('Install Python 3.11') {
                sh '''
                     apt-get update -y
                     apt-get install -y software-properties-common
                     add-apt-repository ppa:deadsnakes/ppa -y
                     apt-get update -y
                     apt-get install -y \
                        python3.11 \
                        python3.11-venv \
                        python3.11-distutils \
                        python3.11-dev
                    python3.11 --version
                '''
            }

            stage('Install pip') {
                sh '''
                    curl -sS https://bootstrap.pypa.io/get-pip.py | python3.11
                    python3.11 -m pip --version
                '''
            }

            stage('Install Poetry') {
                sh '''
                    python3.11 -m pip install --user poetry
                    export PATH="$HOME/.local/bin:$PATH"
                    poetry --version
                '''
            }

            stage('Install System Dependencies') {
                sh '''
                     apt-get update -y
                     apt-get install -y build-essential libpq-dev
                '''
            }

            stage('Install Dependencies') {
                sh '''
                    export PATH="$HOME/.local/bin:$PATH"
                    poetry config virtualenvs.create true
                    poetry config virtualenvs.in-project true
                    poetry install --no-root
                '''
            }

            stage('Run Code Coverage') {
                sh '''
                    export PATH="$HOME/.local/bin:$PATH"
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

            // =================================================
            // Notifications
            // =================================================
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

            // Slack Notification
            slackSend(
                channel: config.slackChannel ?: '#jenkins-alerts',
                message: message,
                tokenCredentialId: config.slackTokenId ?: 'slack-token'
            )

            // Email Notification
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
