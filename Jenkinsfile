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
    booleanParam(defaultValue: true, description: 'Whether build docs or not', name: 'Doxygen'),
    booleanParam(defaultValue: false, description: 'Whether build Java bindings', name: 'JavaBindings'),
    booleanParam(defaultValue: false, description: 'Whether build Python bindings', name: 'PythonBindings'),
    booleanParam(defaultValue: true, description: 'Whether build bindings only w/o Iroha itself', name: 'BindingsOnly'),
    string(defaultValue: '4', description: 'How much parallelism should we exploit. "4" is optimal for machines with modest amount of memory and at least 4 cores', name: 'PARALLELISM')])])

// Trigger Develop build every day
String nightlyBuild = BRANCH_NAME == "develop" ? "@midnight" : ""

pipeline {
    environment {
        CCACHE_DIR = '/opt/.ccache'
        HUNTER_ROOT = '/opt/.hunter'
        SORABOT_TOKEN = credentials('SORABOT_TOKEN')
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        CODECOV_TOKEN = credentials('CODECOV_TOKEN')
        DOCKERHUB = credentials('DOCKERHUB')
        DOCKER_IMAGE = 'hyperledger/iroha-docker-develop:v3'
        DOCKER_BASE_IMAGE_DEVELOP = 'hyperledger/iroha-docker-develop:v3'
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
                            dockerize = load ".jenkinsci/dockerize.groovy"
                            debugBuild = load ".jenkinsci/debug-build.groovy"
                            debugBuild.doDebugBuild()
                            // TODO: move to `Release` as we push image only it this case
                            if ( env.BRANCH_NAME == "master" ) {
                                dockerize.doDockerize()
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
                            def debugBuild = load ".jenkinsci/debug-build.groovy"
                            debugBuild.doDebugBuild()
                            // TODO: move to `Release` as we push image only it this case
                            if ( env.BRANCH_NAME == "master" ) {
                                dockerize.doDockerize()
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
                                ccache --max-size=2G
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
                            sh "ccache --show-stats"
                            
                            // TODO: replace with upload to artifactory server
                            // only develop branch
                            if ( env.BRANCH_NAME == "develop" ) {
                                //archive(includes: 'build/bin/,compile_commands.json')    
                            }                            

                            sh "lcov --capture --directory build --config-file .lcovrc --output-file build/reports/coverage_full.info"
                            sh "lcov --remove build/reports/coverage_full.info '/usr/*' 'schema/*' --config-file .lcovrc -o build/reports/coverage_full_filtered.info"
                            sh "python /tmp/lcov_cobertura.py build/reports/coverage_full_filtered.info -o build/reports/coverage.xml"                                
                            cobertura autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: '**/build/reports/coverage.xml', conditionalCoverageTargets: '75, 50, 0', failUnhealthy: false, failUnstable: false, lineCoverageTargets: '75, 50, 0', maxNumberOfBuilds: 50, methodCoverageTargets: '75, 50, 0', onlyStable: false, zoomCoverageChart: false
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
    post {
        always {
            script {
                sh """
                  docker stop $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
                  docker rm $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
                  docker network rm $IROHA_NETWORK || true
                """
            }
        }
    }
}
