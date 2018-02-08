#!/usr/bin/env groovy

def doDebugBuild() {
	sh "docker network create ${env.IROHA_NETWORK}"

	docker.image('postgres:9.5').run(""
	    + " -e POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
	    + " -e POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
	    + " --name ${env.IROHA_POSTGRES_HOST}"
	    + " --network=${env.IROHA_NETWORK}")

	docker.image('redis:3.2.8').run(""
	    + " --name ${env.IROHA_REDIS_HOST}"
	    + " --network=${env.IROHA_NETWORK}")
}
return this
