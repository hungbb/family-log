// Gia Ký — every Jenkins job of this project, as Job DSL. The giaky-seed job applies this file;
// nothing here is created or edited by hand in the Jenkins UI, because the next seed run would
// overwrite it. Change a job here, merge to main, and giaky-seed brings Jenkins in line.
//
//   giaky-seed                 applies this file (it manages itself too)
//   giaky-staging              build, test and deploy main onto the Jenkins host; polls main
//   giaky-staging-demo-data    fill staging with the demo clan; manual
//   giaky-prod                 build, push and deploy main to the ezgo host; manual
//
// Job DSL reference for this Jenkins: http://192.168.1.100:8080/plugin/job-dsl/api-viewer/index.html
//
// The parameters below repeat each Jenkinsfile's own parameters {} block on purpose: declared here,
// they exist before a job's first build, so Build with Parameters works from the start. Keep the two
// in step when adding one.

def REPO        = 'git@github.com:hungbb/family-log.git'
def CREDENTIALS = 'github-ssh'   // SSH key with read access (a GitHub deploy key is enough)
def BRANCH      = '*/main'

// A pipeline job that reads its Jenkinsfile from main. `extra` adds what differs per job.
def pipelineFromScm = { String jobName, String jenkinsfile, String text, Closure extra ->
    pipelineJob(jobName) {
        description(text)
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(REPO)
                            credentials(CREDENTIALS)
                        }
                        branch(BRANCH)
                    }
                }
                scriptPath(jenkinsfile)
                lightweight(true)
            }
        }
        extra.delegate = delegate
        extra.resolveStrategy = Closure.DELEGATE_FIRST
        extra()
    }
}

pipelineFromScm('giaky-seed', 'deploy/jenkins/Jenkinsfile',
        'Gia Ký — applies deploy/jenkins/jobs.groovy: creates and updates every giaky-* job. Polls main.') {
    properties {
        pipelineTriggers {
            triggers {
                pollSCM { scmpoll_spec('H/15 * * * *') }
            }
        }
    }
}

pipelineFromScm('giaky-staging', 'Jenkinsfile',
        'Gia Ký — build, test and deploy main to staging on this host (http://192.168.1.100:8081). Polls main.') {
    parameters {
        booleanParam('RUN_TESTS', true,
                'Run ./mvnw verify (incl. Testcontainers ITs) and the web verify stage before deploying.')
    }
    properties {
        pipelineTriggers {
            triggers {
                // Swap for a GitHub webhook if Jenkins ever becomes reachable from GitHub.
                pollSCM { scmpoll_spec('H/5 * * * *') }
            }
        }
    }
}

pipelineFromScm('giaky-staging-demo-data', 'deploy/staging/Jenkinsfile.demo-data',
        'Gia Ký — fill staging (http://192.168.1.100:8081) with the demo clan and its four test accounts. Manual; safe to rerun.') {
    parameters {
        stringParam('BASE_URL', 'http://127.0.0.1:8081', 'Where staging answers, as seen from the Jenkins host.')
        stringParam('SEED', '20261112', 'Random seed; the same seed always builds the same tree.')
    }
}

// No trigger on purpose: production moves when someone decides it does.
pipelineFromScm('giaky-prod', 'deploy/prod/Jenkinsfile',
        'Gia Ký — build main and deploy it to production on the ezgo host (https://giaky.etopfunvalue.com). Manual: Build with Parameters.') {
    parameters {
        booleanParam('RUN_TESTS', true, 'Run the API and web test suites before building.')
        stringParam('ROLLBACK_TAG', '',
                'Redeploy an image tag already in the registry (e.g. build-17), skipping test, build and push. Empty for a normal build of main.')
    }
}
