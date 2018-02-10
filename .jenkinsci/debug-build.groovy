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

	def platform = sh(script: 'uname -m', returnStdout: true).trim()
	// TODO: replace Github pull path as soon as multiplatform support will be merged
	sh "curl -L -o /tmp/${env.GIT_COMMIT}/Dockerfile --create-dirs https://raw.githubusercontent.com/hyperledger/iroha/${env.GIT_COMMIT}/docker/develop/${platform}/Dockerfile"
	// pull docker image in case we don't have one
	// speeds up consequent image builds as we simply tag them 
	sh "docker pull ${DOCKER_BASE_IMAGE_DEVELOP}"
	if (env.BRANCH_NAME == 'develop') {
	    iC = docker.build("hyperledger/iroha:develop-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT} --build-arg PARALLELISM=${params.PARALLELISM}")
	}
	else {
	    iC = docker.build("hyperledger/iroha:workflow-${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT} --build-arg PARALLELISM=${params.PARALLELISM}")
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
	    + " -v /var/jenkins/ccache:${CCACHE_DIR}"
	    + " -v /var/jenkins/hunter:${HUNTER_ROOT}") {

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
	    def cmake_options = ""
		if (params.JavaBindings) {
    		cmake_options += " -DSWIG_JAVA=ON "
    	}
    	if (params.PythonBindings) {
    		cmake_options += " -DSWIG_PYTHON=ON "
    	}
	    if (params.BindingsOnly) {
	    	// In case language specific options were not set,
	    	// build for every language
	    	if (!params.JavaBindings && !params.PythonBindings) {
	    		cmake_options += " -DSWIG_JAVA=ON -DSWIG_PYTHON=ON "
	    	}
	    	sh """
	    		cmake \
	    		  -H. \
	    		  -Bbuild \
	    		  ${cmake_options}
	    	"""
	    	sh "cmake --build build --target irohajava -- -j${params.PARALLELISM}"
	    	sh "cmake --build build --target irohapy -- -j${params.PARALLELISM}"
	    	// TODO: publish artifacts
	    }
	    else {	    
		    sh """
		        cmake \
		          -DCOVERAGE=ON \
		          -DTESTING=ON \
		          -H. \
		          -Bbuild \
		          -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
		          -DIROHA_VERSION=${env.IROHA_VERSION} \
		          ${cmake_options}
		    """
		    sh "cmake --build build -- -j${params.PARALLELISM}"
		    sh "ccache --show-stats"
		    if (params.JavaBindings) {
		    	sh "cmake --build build --target irohajava -- -j${params.PARALLELISM}"
		    	// TODO: publish artifacts

		    }
		    if (params.PythonBindings) {
		    	sh "cmake --build build --target irohapy -- -j${params.PARALLELISM}"
		    	// TODO: publish artifacts
		    }
		    sh "cmake --build build --target test"
		    sh "cmake --build build --target cppcheck"	    
		    
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
}
return this
