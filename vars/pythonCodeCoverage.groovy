def call(Map config = [:]) {

    node {

        environment {
            PYENV_ROOT = "${env.HOME}/.pyenv"
            PATH = "${env.HOME}/.local/bin:${env.HOME}/.pyenv/shims:${env.HOME}/.pyenv/bin:${env.PATH}"
        }

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
                    if [ ! -d "$PYENV_ROOT" ]; then
                        curl https://pyenv.run | bash
                    fi

                    eval "$(pyenv init -)"
                    if ! pyenv versions | grep -q "3.11.8"; then
                        pyenv install 3.11.8
                    fi
                    pyenv global 3.11.8

                    python3.11 --version
                '''
            }

            stage('Install Poetry') {
                sh '''
                    python3.11 -m pip install --upgrade pip
                    python3.11 -m pip install --user poetry
                    which poetry
                    poetry --version
                '''
            }

            stage('Install Dependencies') {
                sh '''
                    poetry config virtualenvs.create true
                    poetry config virtualenvs.in-project true
                    poetry install --no-root
                '''
            }

            stage('Run Code Coverage') {
                sh '''
                    poetry run pytest \
                        --cov=. \
                        --cov-report=xml \
                        --cov-report=term
                '''
            }

        } catch (err) {
            status = 'FAILURE'
            throw err

        } finally {
            // Slack and mail unchanged…
        }
    }
}
