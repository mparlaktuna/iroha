#!/usr/bin/env groovy

def doDockerCleanup() {

    sh """
      docker stop $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
      docker rm $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
      docker network rm $IROHA_NETWORK || true
    """
    // Check whether the image is the last-standing man
    // i.e., no other tags exist for this image
    def numImages = sh(script: "docker images | grep ${iC.id} | wc -l", returnStdout: true).trim().toInteger()
    if (numImages > 1) {
    	sh "docker rmi ${iC.id} || true"
    }
}

return this
