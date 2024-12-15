// vars/sharedPipeline.groovy
def call(String portMap) {
    pipeline {
    
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('gitlab-credentials')
        REGISTRY_URL = "registry.gitlab.com/rubenalbi/"
        GITLAB_TOKEN = credentials('gitlab-token')
        SSH_CONNECTION = "ubuntu@ec2-15-188-194-92.eu-west-3.compute.amazonaws.com"
        SSH_CONNECTION_PRE = "ruben@homeserver.rubenalbiach.com"
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
                    env.CI_PROJECT_NAME = props.name
                    env.PORT_MAP = portMap

                    if (env.BRANCH_NAME == 'main') {
                        echo 'I only execute on the main branch'
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
                echo "Port mapping docker ${PORT_MAP}"
                //$CI_REGISTRY_IMAGE:$VERSION-$BRANCH_NAME-$BUILD_NUMBER 
                
            }
        }
        stage('Test') {
             agent {
                docker {
                    image 'node:18.18.0-alpine3.18'
                }
            }
            steps {
                echo 'Testing'
                //sh 'npm run build'
            }
        }
        stage('Docker login') {
            steps {
                sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login registry.gitlab.com -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'
            }
        }
        stage('Build Angular Docker PRE') {
            when {
                not {
                    branch 'main'
                }
            }
            steps {
                echo "Building ${TAG}"
                sh 'docker build --build-arg VERSION="$TAG" --build-arg ENV=pre --pull -t $CI_REGISTRY_IMAGE:$TAG .'
            }
        }
        stage('Build Angular Docker PRO') {
            when {
                branch 'main'
            }
            steps {
                echo "Building ${TAG}"
                sh 'docker build --build-arg VERSION="$TAG" --build-arg ENV=pro --pull -t $CI_REGISTRY_IMAGE:$TAG .'
            }
        }
        stage('Creating TAG') {
                when {
                    branch 'main'
                }
                steps {
                    echo "Creating tag ${TAG}"
                    withCredentials([gitUsernamePassword(credentialsId: 'gitlab-credentials', gitToolName: 'git-tool')]) {
                        sh 'git config --global user.email "ruben.albiach@gmail.com"'
                        sh 'git config --global user.name "Rubén Albiach"'
                        sh 'git tag -l | xargs git tag -d'
                        sh 'git fetch -t'
                        sh 'git tag -a $TAG -m "Jenkins tag"'
                        sh 'git push origin $TAG'
                    }
                }
        }
        stage('Push Docker') {
            steps {
                sh 'docker push $CI_REGISTRY_IMAGE:$TAG'
            }
        }
        stage('Deploy PRE') {
            when {
                branch 'develop'
            }
            steps {
                sshagent(credentials : ['homeserver-key']){
                        sh 'ssh -tt -o StrictHostKeyChecking=no $SSH_CONNECTION_PRE ls -l'
                        sh 'ssh $SSH_CONNECTION_PRE docker ps'
                        sh 'ssh $SSH_CONNECTION_PRE "docker stop $CI_PROJECT_NAME || true"'
                        sh 'ssh $SSH_CONNECTION_PRE "docker rm $CI_PROJECT_NAME || true"'
                        sh 'ssh $SSH_CONNECTION_PRE docker image prune -a -f || true'
                        sh 'ssh $SSH_CONNECTION_PRE docker login -u $GITLAB_TOKEN_USR -p $GITLAB_TOKEN_PSW $REGISTRY_URL'
                        sh 'ssh $SSH_CONNECTION_PRE docker pull $CI_REGISTRY_IMAGE:$TAG'
                        sh 'ssh $SSH_CONNECTION_PRE "docker run --name $CI_PROJECT_NAME -p $PORT_MAP --restart unless-stopped -d $CI_REGISTRY_IMAGE:$TAG"'

                }
            }
        }
        stage('Deploy PRO') {
            when {
                branch 'main'
            }
            steps {
                sshagent(credentials : ['aws-nginx-server']){
                        sh 'ssh -tt -o StrictHostKeyChecking=no $SSH_CONNECTION ls -l'
                        sh 'ssh $SSH_CONNECTION docker ps'
                        sh 'ssh $SSH_CONNECTION "docker stop $CI_PROJECT_NAME || true"'
                        sh 'ssh $SSH_CONNECTION "docker rm $CI_PROJECT_NAME || true"'
                        sh 'ssh $SSH_CONNECTION docker image prune -a -f || true'
                        sh 'ssh $SSH_CONNECTION docker login -u $GITLAB_TOKEN_USR -p $GITLAB_TOKEN_PSW $REGISTRY_URL'
                        sh 'ssh $SSH_CONNECTION docker pull $CI_REGISTRY_IMAGE:$TAG'
                        sh 'ssh $SSH_CONNECTION "docker run --name $CI_PROJECT_NAME -p $PORT_MAP --restart unless-stopped -d $CI_REGISTRY_IMAGE:$TAG"'

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