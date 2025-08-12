pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/openjdk-17'
        PATH = "$JAVA_HOME/bin:$PATH"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts 'target/*.jar'
                }
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Code Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar'
                }
            }
        }
        stage('Deploy') {
            steps {
                sh 'docker build -t my-java-app:latest .'
                sh 'docker push my-registry/my-java-app:latest'
            }
        }
    }
    post {
        success {
            slackSend channel: '#deployments', message: 'Deployment succeeded!'
        }
        failure {
            slackSend channel: '#failures', message: 'Pipeline failed!'
        }
    }
}
