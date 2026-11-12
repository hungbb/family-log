// Gia Ký — staging pipeline. Builds, tests and deploys onto the Jenkins host itself.
//
// Jenkins credentials this expects (Manage Jenkins → Credentials, kind "Secret text"):
//   giaky-staging-db-password  — password of the `postgres` role on 192.168.1.100:5432
//   giaky-staging-jwt-secret   — `openssl rand -base64 32`; rotating it signs everyone out
//
// The agent needs only Docker (the jenkins user in the `docker` group) and curl. The JDK and Node
// both come from containers: the host has a JRE 21 but no javac, and Node only under nvm.

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'RUN_TESTS', defaultValue: true,
                description: 'Run ./mvnw verify (incl. Testcontainers ITs) and the web verify stage before deploying.')
    }

    environment {
        IMAGE_TAG       = "staging-${env.BUILD_NUMBER}"
        COMPOSE_FILE    = 'deploy/staging/docker-compose.yml'
        PUBLIC_URL      = 'http://192.168.1.100:8081'
        WEB_PORT        = '8081'
        API_PORT        = '8180'
        DB_URL          = 'jdbc:postgresql://192.168.1.100:5432/giaky'
        DB_USERNAME     = 'postgres'
        DOCKER_BUILDKIT = '1'
    }

    stages {
        stage('Test API') {
            when { expression { params.RUN_TESTS } }
            steps {
                // `verify`, not `test`: the *IT suites are bound to verify (ADR 0001).
                // Testcontainers drives the host daemon through the mounted socket; host networking
                // lets it reach the ports it maps. Running as the jenkins uid keeps target/ removable,
                // and ~/.m2 is shared across builds as the Maven cache.
                sh '''
                    mkdir -p "$HOME/.m2"
                    docker run --rm --network host \
                        -u "$(id -u):$(id -g)" --group-add "$(stat -c %g /var/run/docker.sock)" \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        -v "$WORKSPACE/api:/src" -w /src \
                        -v "$HOME/.m2:/m2" -e MAVEN_USER_HOME=/m2 -e HOME=/tmp \
                        eclipse-temurin:21-jdk \
                        sh -c 'chmod +x mvnw && ./mvnw -B -ntp -Dmaven.repo.local=/m2/repository verify'
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'api/target/surefire-reports/*.xml, api/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Verify web') {
            when { expression { params.RUN_TESTS } }
            steps {
                sh 'docker build --target verify --build-context repo-root=. -t giaky-web:verify web'
            }
        }

        stage('Build images') {
            parallel {
                stage('api') {
                    steps {
                        sh 'docker build -t giaky-api:$IMAGE_TAG -t giaky-api:staging api'
                    }
                }
                stage('web') {
                    steps {
                        sh 'docker build --target runtime -t giaky-web:$IMAGE_TAG -t giaky-web:staging web'
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    string(credentialsId: 'giaky-staging-db-password', variable: 'DB_PASSWORD'),
                    string(credentialsId: 'giaky-staging-jwt-secret',  variable: 'JWT_SECRET'),
                ]) {
                    sh 'docker compose -f "$COMPOSE_FILE" up -d --remove-orphans'
                }
            }
        }

        stage('Smoke test') {
            steps {
                // Flyway migrates on startup, so the first boot after a schema change takes longer.
                sh '''
                    for i in $(seq 1 60); do
                        if curl -fsS "http://127.0.0.1:$WEB_PORT/actuator/health" | grep -q '"status":"UP"'; then
                            echo "API healthy through nginx after $((i * 3))s"
                            curl -fsS -o /dev/null "http://127.0.0.1:$WEB_PORT/" && echo "Web client served"
                            exit 0
                        fi
                        sleep 3
                    done
                    echo "Staging did not become healthy in 180s" >&2
                    docker compose -f "$COMPOSE_FILE" ps
                    docker compose -f "$COMPOSE_FILE" logs --tail=200 api
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            echo "Staging deployed: ${env.PUBLIC_URL}  (build ${env.IMAGE_TAG})"
            // Keep the three newest numbered tags of each image; `:staging` always points at the live one.
            sh '''
                for repo in giaky-api giaky-web; do
                    docker images "$repo" --format '{{.Tag}}' \
                        | grep -E '^staging-[0-9]+$' | sort -t- -k2 -n -r | tail -n +4 \
                        | xargs -r -I{} docker rmi "$repo:{}" || true
                done
                docker image prune -f >/dev/null || true
            '''
        }
    }
}
