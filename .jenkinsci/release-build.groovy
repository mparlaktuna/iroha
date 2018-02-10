#!/usr/bin/env groovy

def doReleaseBuild() {
	def platform = sh(script: 'uname -m', returnStdout: true).trim()
	// TODO: replace Github pull path as soon as multiplatform support will be merged
	// TODO: change docker release-build image such that could possibly build the release package
	sh "curl -L -o /tmp/${env.GIT_COMMIT}/Dockerfile --create-dirs https://raw.githubusercontent.com/hyperledger/iroha/${env.GIT_COMMIT}/docker/develop/${platform}/Dockerfile"
	// pull docker image for building release package of Iroha
	// speeds up consequent image builds as we simply tag them 
	sh "docker pull ${DOCKER_BASE_IMAGE_RELEASE_BUILD}"
	if (env.BRANCH_NAME == 'master') {
	    iC = docker.build("hyperledger/iroha:${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
	}
	else {
		iC = docker.build("hyperledger/iroha-workflow-release:${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
	}

	sh "mkdir /tmp/${env.GIT_COMMIT}-${BUILD_NUMBER}"
	iC.inside(""
		+ " -v /tmp/${GIT_COMMIT}-${BUILD_NUMBER}:/tmp/${GIT_COMMIT}"
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
		          -H. \
		          -Bbuild \
		          -DCMAKE_BUILD_TYPE=${params.BUILD_TYPE} \
		          -DIROHA_VERSION=${env.IROHA_VERSION} \
		          ${cmake_options}
		    """
		    sh "cmake --build build -- -j${params.PARALLELISM}"
		    sh "ccache --show-stats"
		    // copy build package to the volume
		    sh "cp ${IROHA_BUILD}/iroha.deb /tmp/${GIT_COMMIT}"
		    
		    if (params.JavaBindings) {
		    	sh "cmake --build build --target irohajava -- -j${params.PARALLELISM}"
		    	// TODO: publish artifacts

		    }
		    if (params.PythonBindings) {
		    	sh "cmake --build build --target irohapy -- -j${params.PARALLELISM}"
		    	// TODO: publish artifacts
		    }
			
			sh "lcov --capture --directory build --config-file .lcovrc --output-file build/reports/coverage_full.info"
		    sh "lcov --remove build/reports/coverage_full.info '/usr/*' 'test/*' 'schema/*' --config-file .lcovrc -o build/reports/coverage_full_filtered.info"
		    sh "python /tmp/lcov_cobertura.py build/reports/coverage_full_filtered.info -o build/reports/coverage.xml"                                
		    cobertura autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: '**/build/reports/coverage.xml', conditionalCoverageTargets: '75, 50, 0', failUnhealthy: false, failUnstable: false, lineCoverageTargets: '75, 50, 0', maxNumberOfBuilds: 50, methodCoverageTargets: '75, 50, 0', onlyStable: false, zoomCoverageChart: false
		}   
	}

	sh "curl -L -o /tmp/${env.GIT_COMMIT}/Dockerfile --create-dirs https://raw.githubusercontent.com/hyperledger/iroha/${env.GIT_COMMIT}/docker/release/${platform}/Dockerfile"
	// pull docker Iroha release image
	// speeds up consequent image builds as we simply tag them 
	// TODO: iroha.deb package is now in the /tmp/${GIT_COMMIT}-${BUILD_NUMBER} directory. Think on how to add it to the release Dockerfile
	sh "docker pull ${DOCKER_BASE_IMAGE_RELEASE}"
	if (env.BRANCH_NAME == 'master') {
	    iC = docker.build("hyperledger/iroha:${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
	    docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-credentials'){
        	iC.push("${platform}")
        }
	}
	else {
		iC = docker.build("hyperledger/iroha-workflow-release:${GIT_COMMIT}-${BUILD_NUMBER}", "-f /tmp/${env.GIT_COMMIT}/Dockerfile /tmp/${env.GIT_COMMIT}")
	}
	// remove folder with iroha.deb package and Dockerfiles
	sh """
		rm -rf /tmp/${env.GIT_COMMIT}-${BUILD_NUMBER}
		rm -rf /tmp/${env.GIT_COMMIT}
	"""

}
return this
