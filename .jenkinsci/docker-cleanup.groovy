#!/usr/bin/env groovy

def doDockerCleanup() {

    sh """
      docker stop $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
      docker rm $IROHA_POSTGRES_HOST $IROHA_REDIS_HOST || true
      docker rmi ${iC.id} || true
      docker network rm $IROHA_NETWORK || true
    """
}

return this
