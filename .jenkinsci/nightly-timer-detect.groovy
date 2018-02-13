// check if the job was started by a timer
@NonCPS
def isJobStartedByTimer() {
    def startedByTimer = false
    try {
        def buildCauses = currentBuild.rawBuild.getCauses()
        for ( buildCause in buildCauses ) {
            if (buildCause != null) {
                def causeDescription = buildCause.getShortDescription()
                echo "shortDescription: ${causeDescription}"
                if (causeDescription.contains("Started by timer")) {
                    startedByTimer = true
                }
            }
        }
    } catch(theError) {
        echo "Error getting build cause"
    }
 
    return startedByTimer
}

void createPipelineTriggers() {
    script {
        def triggers = []
        if (env.BRANCH_NAME == 'develop') {
            // Run a nightly only for maste
            triggers = [cron('0 21 * * *')]
            startedByTimer = fnc.isJobStartedByTimer()
            if ( startedByTimer )
            {
                sh """
                    echo ================================================================================================
                    echo ===================================THIS JOB IS STARTED BY TIMER=================================
                    echo ================================================================================================
                """
            }
        }
        properties([
                pipelineTriggers(triggers)
        ])
    }
}
return this

startedByTimer = false
                    // set cron job for running pipeline at nights
                    if (env.BRANCH_NAME == "develop") {
                        fnc = load ".jenkinsci/nightly-timer-detect.groovy"
                     
                    }