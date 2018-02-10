#!/usr/bin/env groovy

def doDockerize() {
    
    sh "cp ${IROHA_BUILD}/iroha.deb ${IROHA_RELEASE}/iroha.deb"

    if (!( env.BRANCH_NAME == "develop" || env.BRANCH_NAME == "master")) {
        return 
    }

    env.IMAGE_NAME = ""

    if ( env.BRANCH_NAME == "develop" ) {
        env.IMAGE_NAME = env.DOCKER_BASE_IMAGE_DEVELOP
    }
    elif ( env.BRANCH_NAME == "master" ) {
        env.IMAGE_NAME = env.DOCKER_BASE_IMAGE_RELEASE
    }
    
    // build only in case we commit into develop or master -- not PR!!
    def platform = sh(script: 'uname -m', returnStdout: true).trim()
    app = docker.build("${env.IMAGE_NAME}")
    docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-credentials'){
        app.push("${platform}")
    }
}
return this
