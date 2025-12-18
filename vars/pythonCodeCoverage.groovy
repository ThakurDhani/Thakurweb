def call(Map config = [:]) {

    podTemplate(
        containers: [
            containerTemplate(
                name: 'python',
                image: 'python:3.11-slim',
                ttyEnabled: true,
                command: 'cat'
            )
        ]
    ) {

        node(POD_LABEL) {

            def repoUrl     = config.repoUrl
            def branch      = config.branch ?: 'main'
            def projectName = config.projectName ?: 'Python Code Coverage'

            if (!repoUrl) {
                error "repoUrl is required"
            }

            def status = 'SUCCESS'

            try {

                /* =======================================================
                   CHECKOUT
                ======================================================= */
                stage('Checkout') {
                    git branch: branch, url: repoUrl
                }

                /* =======================================================
                   PYTHON VERSION
                ======================================================= */
                stage('Verify Python') {
                    container('python') {
                        sh "python3 --version"
                    }
                }

                /* =======================================================
                   INSTALL PIP
                ======================================================= */
                stage('Install pip') {
                    container('python') {
                        sh '''
                            curl -sS https://bootstrap.pypa.io/get-pip.py -o get-pip.py
                            python3 get-pip.py
                            pip --version
                        '''
                    }
                }

                /* =======================================================
                   INSTALL POETRY
                ======================================================= */
                stage('Install Poetry') {
                    container('python') {
                        sh '''
                            pip install poetry
                            poetry --version
                        '''
                    }
                }

                /* =======================================================
                   INSTALL DEPENDENCIES
                ======================================================= */
                stage('Install Dependencies') {
                    container('python') {
                        sh '''
                            poetry config virtualenvs.create true
                            poetry config virtualenvs.in-project true
                            poetry install --no-root
                        '''
                    }
                }

                /* =======================================================
                   RUN CODE COVERAGE
                ======================================================= */
                stage('Run Code Coverage') {
                    container('python') {
                        sh '''
                            poetry run pytest \
                                --ignore=client/tests/test_postgres_conn.py \
                                --ignore=client/tests/test_redis_conn.py \
                                --cov=. \
                                --cov-report=term \
                                --cov-report=xml
                        '''
                    }
                }

            } catch (err) {

                status = 'FAILURE'
                throw err

            } finally {

                /* =======================================================
                   NOTIFICATIONS
                ======================================================= */
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
                    echo "Email notification failed"
                }
            }
        }
    }
}
