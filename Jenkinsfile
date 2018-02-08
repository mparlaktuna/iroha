// Overall pipeline looks like the following
//               
//   |--Linux-----|----Debug
//   |            |----Release 
//   |    OR
//   |           
//-- |--Linux ARM-|----Debug
//   |            |----Release
//   |    OR
//   |
//   |--MacOS-----|----Debug
//   |            |----Release
properties([parameters([
    choice(choices: 'Debug\nRelease', description: '', name: 'BUILD_TYPE'),
    booleanParam(defaultValue: true, description: '', name: 'Linux'),
    booleanParam(defaultValue: false, description: '', name: 'ARM'),
    booleanParam(defaultValue: false, description: '', name: 'MacOS'),
    booleanParam(defaultValue: true, description: 'Whether we should build docs or not', name: 'Doxygen'),
    string(defaultValue: '4', description: 'How much parallelism should we exploit. "4" is optimal for machines with modest amount of memory and at least 4 cores', name: 'PARALLELISM')])])

// Trigger Develop build every day
String nightlyBuild = BRANCH_NAME == "develop" ? "@midnight" : ""

pipeline {
    environment {
        CCACHE_DIR = '/opt/.ccache'
        SORABOT_TOKEN = credentials('SORABOT_TOKEN')
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        CODECOV_TOKEN = credentials('CODECOV_TOKEN')
        DOCKERHUB = credentials('DOCKERHUB')
        DOCKER_IMAGE = 'hyperledger/iroha-docker-develop:v1'
        DOCKER_BASE_IMAGE_DEVELOP = 'hyperledger/iroha-docker-develop:v1'
        DOCKER_BASE_IMAGE_RELEASE = 'hyperledger/iroha-docker'

        IROHA_NETWORK = "iroha-${GIT_COMMIT}-${BUILD_NUMBER}"
        IROHA_POSTGRES_HOST = "pg-${GIT_COMMIT}-${BUILD_NUMBER}"
        IROHA_POSTGRES_USER = "pg-user-${GIT_COMMIT}"
        IROHA_POSTGRES_PASSWORD = "${GIT_COMMIT}"
        IROHA_REDIS_HOST = "redis-${GIT_COMMIT}-${BUILD_NUMBER}"
        IROHA_POSTGRES_PORT = 5432
        IROHA_REDIS_PORT = 6379
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    // triggers {
    //     parameterizedCron('''
    //         nightlyBuild %ARM=True;MacOS=True
    //     ''')
    // }
    agent any
    stages {
        stage ('Stop same job builds') {
            agent { label 'master' }
            steps {
                script {
                    // Stop same job running builds if any
                    def builds = load ".jenkinsci/cancel-builds-same-job.groovy"
                    builds.cancelSameCommitBuilds()
                }
            }
        }
        stage('Build Debug') {
            when { expression { params.BUILD_TYPE == 'Debug' } }
            parallel {
                stage ('Linux') {                    
                    when { expression { return params.Linux } }
                    agent { label 'linux && x86_64' }
                    steps {
                        script {
                            def dockerize = load ".jenkinsci/dockerize.groovy"

                            sh "docker network create ${env.IROHA_NETWORK}"

                            docker.image('postgres:9.5').run(""
                                + " -e POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
                                + " -e POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
                                + " --name ${env.IROHA_POSTGRES_HOST}"
                                + " --network=${env.IROHA_NETWORK}")

                            docker.image('redis:3.2.8').run(""
                                + " --name ${env.IROHA_REDIS_HOST}"
                                + " --network=${env.IROHA_NETWORK}")

                            def platform = sh(script: 'uname -m', returnStdout: true).trim()
                            // TODO: replace Github pull path as soon as multiplatform support will be merged
                            sh "curl -L -o /tmp/${env.GIT_COMMIT}/Dockerfile --create-dirs https://raw.githubusercontent.com/hyperledger/iroha/${env.GIT_COMMIT}/docker/develop/${platform}/Dockerfile"
                            // pull docker image in case we don't have one
                            // speeds up consequent image builds as we simply tag them 
                            sh "docker pull ${DOCKER_BASE_IMAGE_DEVELOP}"
                            if (env.BRANCH_NAME == 'develop') {
                                iC = docker.build("hyperledger/iroha:develop-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
                                // TODO: push image with `develop` tag
                            }
                            else {
                                iC = docker.build("hyperledger/iroha:workflow-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
                            }
                            sh "rm -rf /tmp/${env.GIT_COMMIT}"
                            iC.inside(""
                                + " -e IROHA_POSTGRES_HOST=${env.IROHA_POSTGRES_HOST}"
                                + " -e IROHA_POSTGRES_PORT=${env.IROHA_POSTGRES_PORT}"
                                + " -e IROHA_POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
                                + " -e IROHA_POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
                                + " -e IROHA_REDIS_HOST=${env.IROHA_REDIS_HOST}"
                                + " -e IROHA_REDIS_PORT=${env.IROHA_REDIS_PORT}"
                                + " --network=${env.IROHA_NETWORK}"
                                + " -v /var/jenkins/ccache:${CCACHE_DIR}") {

                                def scmVars = checkout scm
                                env.IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
                                env.IROHA_HOME = "/opt/iroha"
                                env.IROHA_BUILD = "${env.IROHA_HOME}/build"
                                env.IROHA_RELEASE = "${env.IROHA_HOME}/docker/release"

                                sh """
                                    ccache --version
                                    ccache --show-stats
                                    ccache --zero-stats
                                    ccache --max-size=2G
                                """
                                sh """
                                    cmake \
                                      -DCOVERAGE=ON \
                                      -DTESTING=ON \
                                      -H. \
                                      -Bbuild \
                                      -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
                                      -DIROHA_VERSION=${env.IROHA_VERSION} \
                                      -DCMAKE_CXX_FLAGS=-O1
                                """
                                sh "cmake --build build -- -j${params.PARALLELISM}"
                                sh "ccache --show-stats"

                                sh "cmake --build build --target test"
                                //sh "cmake --build build --target gcovr"
                                sh "cmake --build build --target cppcheck"

                                if ( env.BRANCH_NAME == "master" ||
                                     env.BRANCH_NAME == "develop" ) {
                                    dockerize.doDockerize()
                                }
                                
                                // Codecov
                                //sh "bash <(curl -s https://codecov.io/bash) -f build/reports/gcovr.xml -t ${CODECOV_TOKEN} || echo 'Codecov did not collect coverage reports'"

                                // // Sonar
                                // if (env.CHANGE_ID != null) {
                                //     sh """
                                //         sonar-scanner \
                                //             -Dsonar.github.disableInlineComments \
                                //             -Dsonar.github.repository='hyperledger/iroha' \
                                //             -Dsonar.analysis.mode=preview \
                                //             -Dsonar.login=${SONAR_TOKEN} \
                                //             -Dsonar.projectVersion=${BUILD_TAG} \
                                //             -Dsonar.github.oauth=${SORABOT_TOKEN} \
                                //             -Dsonar.github.pullRequest=${CHANGE_ID}
                                //     """
                                // }
                                //stash(allowEmpty: true, includes: 'build/compile_commands.json', name: 'Compile commands')
                                //stash(allowEmpty: true, includes: 'build/reports/', name: 'Reports')
                                //archive(includes: 'build/bin/,compile_commands.json')
                                sh "lcov --capture --directory build --config-file .lcovrc --output-file build/reports/coverage_full.info"
                                sh "lcov --remove build/reports/coverage_full.info '/usr/*' 'schema/*' --config-file .lcovrc -o build/reports/coverage_full_filtered.info"
                                sh "python /tmp/lcov_cobertura.py build/reports/coverage_full_filtered.info -o build/reports/coverage.xml"                                
                                cobertura autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: '**/build/reports/coverage.xml', conditionalCoverageTargets: '75, 50, 0', failUnhealthy: false, failUnstable: false, lineCoverageTargets: '75, 50, 0', maxNumberOfBuilds: 50, methodCoverageTargets: '75, 50, 0', onlyStable: false, zoomCoverageChart: false

                            }
                        }
                    }
                    post {
                        always {
                            script {
                                def cleanup = load ".jenkinsci/docker-cleanup.groovy"
                                cleanup.doDockerCleanup()
                                cleanWs()
                            }
                        }
                    }
                }            
                stage('ARM') {
                    when { expression { return params.ARM } }
                    agent { label 'arm' }
                    steps {
                        script {
                            def dockerize = load ".jenkinsci/dockerize.groovy"
                            
                            // Stop same job running builds if any
                            def builds = load ".jenkinsci/cancel-builds-same-job.groovy"
                            builds.cancelSameCommitBuilds()

                            sh "docker network create ${env.IROHA_NETWORK}"

                            def p_c = docker.image('postgres:9.5').run(""
                                + " -e POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
                                + " -e POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
                                + " --name ${env.IROHA_POSTGRES_HOST}"
                                + " --network=${env.IROHA_NETWORK}")

                            def r_c = docker.image('redis:3.2.8').run(""
                                + " --name ${env.IROHA_REDIS_HOST}"
                                + " --network=${env.IROHA_NETWORK}")

                            def platform = sh(script: 'uname -m', returnStdout: true).trim()
                            // TODO: replace Github pull path as soon as multiplatform support will be merged
                            sh "curl -L -o /tmp/${env.GIT_COMMIT}/Dockerfile --create-dirs https://raw.githubusercontent.com/hyperledger/iroha/${env.GIT_COMMIT}/docker/develop/${platform}/Dockerfile"
                            // pull docker image in case we don't have one
                            // speeds up consequent image builds as we simply tag them 
                            sh "docker pull ${DOCKER_BASE_IMAGE_DEVELOP}"
                            if (env.BRANCH_NAME == 'develop') {
                                iC = docker.build("hyperledger/iroha:develop-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
                                // TODO: push image with `develop` tag
                            }
                            else {
                                iC = docker.build("hyperledger/iroha:workflow-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
                            }
                            sh "rm -rf /tmp/${env.GIT_COMMIT}"

                            iC.inside(""
                                + " -e IROHA_POSTGRES_HOST=${env.IROHA_POSTGRES_HOST}"
                                + " -e IROHA_POSTGRES_PORT=${env.IROHA_POSTGRES_PORT}"
                                + " -e IROHA_POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
                                + " -e IROHA_POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
                                + " -e IROHA_REDIS_HOST=${env.IROHA_REDIS_HOST}"
                                + " -e IROHA_REDIS_PORT=${env.IROHA_REDIS_PORT}"
                                + " --network=${env.IROHA_NETWORK}"
                                + " -v /var/jenkins/ccache:${CCACHE_DIR}") {

                                def scmVars = checkout scm
                                env.IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
                                env.IROHA_HOME = "/opt/iroha"
                                env.IROHA_BUILD = "${env.IROHA_HOME}/build"
                                env.IROHA_RELEASE = "${env.IROHA_HOME}/docker/release"

                                sh """
                                    ccache --version
                                    ccache --show-stats
                                    ccache --zero-stats
                                    ccache --max-size=1G
                                """
                                sh """
                                    cmake \
                                      -DCOVERAGE=ON \
                                      -DTESTING=ON \
                                      -H. \
                                      -Bbuild \
                                      -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
                                      -DIROHA_VERSION=${env.IROHA_VERSION} \
                                      -DCMAKE_CXX_FLAGS -O1
                                """
                                sh "cmake --build build -- -j${params.PARALLELISM}"
                                sh "ccache --cleanup"
                                sh "ccache --show-stats"

                                //sh "cmake --build build --target test"
                                sh "cd build && ctest --output-on-failure"
                                sh "cmake --build build --target gcovr"
                                sh "cmake --build build --target cppcheck"

                                if ( env.BRANCH_NAME == "master" ||
                                     env.BRANCH_NAME == "develop" ) {
                                    dockerize.doDockerize()
                                }
                            
                                // Codecov
                                sh "bash <(curl -s https://codecov.io/bash) -f build/reports/gcovr.xml -t ${CODECOV_TOKEN} || echo 'Codecov did not collect coverage reports'"

                                // Skip SonarQube checks to speed up builds

                                //stash(allowEmpty: true, includes: 'build/compile_commands.json', name: 'Compile commands')
                                //stash(allowEmpty: true, includes: 'build/reports/', name: 'Reports')
                                //archive(includes: 'build/bin/,compile_commands.json')
                            }
                        }
                    }
                    post {
                        always {
                            script {
                                def cleanup = load ".jenkinsci/docker-cleanup.groovy"
                                cleanup.doDockerCleanup()
                                cleanWs()
                            }
                        }
                    }
                }
                stage('MacOS'){
                    when { expression { return  params.MacOS } }
                    agent { label 'mac' }
                    steps {
                        script {
                            def scmVars = checkout scm
                            env.IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
                            env.IROHA_HOME = "/opt/iroha"
                            env.IROHA_BUILD = "${env.IROHA_HOME}/build"
                            env.CCACHE_DIR = "${env.IROHA_HOME}/.ccache"

                            sh """
                                ccache --version
                                ccache --show-stats
                                ccache --zero-stats
                                ccache --max-size=1G
                            """
                            sh """
                                cmake \
                                  -DCOVERAGE=ON \
                                  -DTESTING=ON \
                                  -H. \
                                  -Bbuild \
                                  -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
                                  -DIROHA_VERSION=${env.IROHA_VERSION}
                            """
                            sh "cmake --build build -- -j${params.PARALLELISM}"
                            sh "ccache --cleanup"
                            sh "ccache --show-stats"
                            
                            // TODO: replace with upload to artifactory server
                            //archive(includes: 'build/bin/,compile_commands.json')
                        }
                    }
                    post {
                        always {
                            script {
                                cleanWs()
                            }
                        }
                    }
                }
            }
        }
        stage('Build Release') {
            when { expression { params.BUILD_TYPE == 'Release' } }
            parallel {
                stage('Linux') {
                    when { expression { return params.Linux } }
                    steps {
                        script {
                            // TODO: pull base image release
                            //sh "docker pull ${DOCKER_BASE_IMAGE_RELEASE}"
                            def scmVars = checkout scm
                            env.IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
                        }
                        sh """
                            ccache --version
                            ccache --show-stats
                            ccache --zero-stats
                            ccache --max-size=1G
                        """
                        sh """
                            cmake \
                              -DCOVERAGE=OFF \
                              -DTESTING=OFF \
                              -H. \
                              -Bbuild \
                              -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
                              -DPACKAGE_DEB=ON \
                              -DPACKAGE_TGZ=ON \
                              -DIROHA_VERSION=${IROHA_VERSION}
                        """
                        sh "cmake --build build -- -j${params.PARALLELISM}"
                        sh "ccache --cleanup"
                        sh "ccache --show-stats"
                        sh """
                        mv build/iroha-{*,linux}.deb && mv build/iroha-{*,linux}.tar.gz
                        echo ${IROHA_VERSION} > version.txt
                        """
                        archive(includes: 'build/iroha-linux.deb,build/iroha-linux.tar.gz,build/version.txt')
                    }
                }
                stage('ARM') {
                    when { expression { return params.ARM } }
                    steps {
                        sh "echo ARM build will be running there"
                    }                        
                }
                stage('MacOS') {
                    when { expression { return params.MacOS } }                        
                    steps {
                        sh "MacOS build will be running there"
                    }
                }
            }
        }
        stage('SonarQube') {
            when { expression { params.BUILD_TYPE == 'Release' } }
            steps {
                sh """
                    if [ -n ${SONAR_TOKEN} ] && \
                      [ -n ${BUILD_TAG} ] && \
                      [ -n ${BRANCH_NAME} ]; then
                      sonar-scanner \
                        -Dsonar.login=${SONAR_TOKEN} \
                        -Dsonar.projectVersion=${BUILD_TAG} \
                        -Dsonar.branch=${BRANCH_NAME}
                    else
                      echo 'required env vars not found'
                    fi
                """
            }
        }
        stage('Build docs') {
            // build docs on any vacant node. Prefer `linux` over 
            // others as nodes are more powerful
            agent { label 'linux || mac || arm' }
            when { 
                allOf {
                    expression { return params.Doxygen }
                    expression { BRANCH_NAME ==~ /(master|develop)/ }
                }
            }
            steps {
                script {
                    def doxygen = load ".jenkinsci/doxygen.groovy"
                    docker.image("${env.DOCKER_IMAGE}").inside {
                        def scmVars = checkout scm
                        doxygen.doDoxygen()
                    }
                }
            }
        }
    }
}
