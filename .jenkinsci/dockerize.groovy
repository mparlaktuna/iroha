#!/usr/bin/env groovy

def doDockerize() {
    
    sh "cp ${IROHA_BUILD}/iroha.deb ${IROHA_RELEASE}/iroha.deb"

    if (!( env.BRANCH_NAME == "develop" || env.BRANCH_NAME == "master")) {
        return 
    }

    env.TAG = ""
    env.IMAGE_NAME = ""

    if ( env.BRANCH_NAME == "develop" ) {
        env.IMAGE_NAME = env.DOCKER_BASE_IMAGE_DEVELOP
    }
    elif ( env.BRANCH_NAME == "master" ) {
        env.IMAGE_NAME = env.DOCKER_BASE_IMAGE_RELEASE
    }
    
    // build only in case we commit into develop or master -- not PR!!
    sh "TAG=`uname -m`"
    app = docker.build("${env.IMAGE_NAME}")
    docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-credentials'){
        app.push("${env.TAG}")
    }
}
return this
