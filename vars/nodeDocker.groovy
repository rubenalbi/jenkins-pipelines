// vars/sharedPipeline.groovy
def call() {
    pipeline {
    
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('gitlab-credentials')
        REGISTRY_URL = "registry.gitlab.com/rubenalbi/"
        GITLAB_TOKEN = credentials('gitlab-token')
        SSH_CONNECTION = "ubuntu@13.37.81.165"
        DOCKER_PATH = "/opt/docker/"
    }
    stages {
        stage("Configure") {
            steps {
                echo "Setting job variables..."
                script {
                    def props = readJSON(file: 'package.json')
                    env.VERSION = props.version
                    env.CI_REGISTRY_IMAGE = env.REGISTRY_URL + props.name
                    env.COMPOSE_PATH = env.DOCKER_PATH + props.name

                    if (env.BRANCH_NAME == 'master') {
                        echo 'I only execute on the master branch'
                        env.TAG = env.VERSION
                    } else if (env.BRANCH_NAME == 'develop') {
                        echo 'I execute on the develop branch'
                        env.TAG = env.VERSION + "-" + env.BUILD_NUMBER
                    } else {
                        echo ' I execute everything else'
                        env.TAG = env.VERSION + "-" + env.BRANCH_NAME + "-" + env.BUILD_NUMBER
                    }
                    env.TAG = env.TAG.replace("/", "-")
                }
                echo "${TAG}"
                echo "${CI_REGISTRY_IMAGE}"
                echo "${env.VERSION}"
                echo "${VERSION}"
                //$CI_REGISTRY_IMAGE:$VERSION-$BRANCH_NAME-$BUILD_NUMBER 
                
            }
        }
        stage('Test') {
             agent {
                docker {
                    image 'node:18.18.0-alpine3.18' 
                    args '-p 3000:3000' 
                }
            }
            steps {
                echo 'Testing'
                sh 'npm run test'
            }
        }
        stage('Build') {
             agent {
                docker {
                    image 'node:18.18.0-alpine3.18' 
                    args '-p 3000:3000' 
                }
            }
            steps {
                sh 'npm install'
            }
        }
        stage('Docker login') {
            steps {
                sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login registry.gitlab.com -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'
            }
        }
        stage('Build docker') {
            steps {
                echo "Building ${VERSION}"
                sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login registry.gitlab.com -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'
                sh 'docker build --pull -t $CI_REGISTRY_IMAGE:$TAG .'
            }
        }
        stage('Push') {
            steps {
                sh 'docker push $CI_REGISTRY_IMAGE:$TAG'
            }
        }
        stage('Deploy') {
            when {
                branch 'develop'
            }
            steps {
                sshagent(credentials : ['aws-nginx-server']){
                        sh 'ssh -tt -o StrictHostKeyChecking=no $SSH_CONNECTION ls -l'
                        sh 'ssh $SSH_CONNECTION docker ps'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose down || true"'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose rm || true"'
                        sh 'ssh $SSH_CONNECTION docker image prune -a -f || true'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && echo \'VERSION=$TAG\' > .env || true"'
                        sh 'ssh $SSH_CONNECTION docker login -u $GITLAB_TOKEN_USR -p $GITLAB_TOKEN_PSW $REGISTRY_URL'
                        sh 'ssh $SSH_CONNECTION docker pull $CI_REGISTRY_IMAGE:$TAG'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose --env-file .env up -d"'

                }
            }
        }
    }
    post {
        always {
            sh 'docker logout'
        }
        failure {
            mail to: 'ruben.albiach@gmail.com',
            subject: "[Jenkins] ${currentBuild.currentResult} ${env.JOB_NAME}",
            body: " ${currentBuild.currentResult}.\n Ha fallado la generación de la imagen de jenkins docker: ${env.BUILD_URL}"
        }
  }
}
} 