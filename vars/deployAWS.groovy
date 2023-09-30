pipeline {
    agent any
    environment {
        GITLAB_TOKEN = credentials('gitlab-token')
        REGISTRY_URL = "registry.gitlab.com/rubenalbi"
        SSH_CONNECTION = "ubuntu@13.37.81.165"
        COMPOSE_PATH = "/opt/docker/ms-node-auth"
    }
    stages {
        
        stage('Test 1') {
            steps {
                sshagent(credentials : ['aws-nginx-server']){

                        sh 'ssh -tt -o StrictHostKeyChecking=no $SSH_CONNECTION ls -l'
                        sh 'ssh $SSH_CONNECTION docker ps'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose down || true"'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose rm || true"'
                        sh 'ssh $SSH_CONNECTION docker image prune -a -f || true'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && echo \'VERSION=1.0.0-2\' > .env || true"'
                        sh 'ssh $SSH_CONNECTION docker login -u $GITLAB_TOKEN_USR -p $GITLAB_TOKEN_PSW $REGISTRY_URL'
                        
                        sh 'ssh $SSH_CONNECTION docker pull $REGISTRY_URL/ms-node-auth:1.0.0-2'
                        sh 'ssh $SSH_CONNECTION "cd $COMPOSE_PATH && docker-compose --env-file .env up -d"'

                }
            }
        }
    }
    post {
        failure {
            mail to: 'ruben.albiach@gmail.com',
            subject: "[Jenkins] ${currentBuild.currentResult} ${env.JOB_NAME}",
            body: "La copia de seguridad de Nextcloud ha terminado en ${currentBuild.currentResult}.\nMás información en la tarea: ${env.BUILD_URL}"
        }
    }
}