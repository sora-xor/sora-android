@Library('jenkins-library@duty/sonar_dojo_ci' ) _

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(steps: this,
    sonar: true,
    sonarProjectName: 'sora-passport-android',
    sonarProjectKey: 'sora:sora-passport-android',
    testCmd: 'ktlint clean runModuleTests jacocoTestReport',
    jobParams: jobParams,
    appPushNoti: true,
    gitUpdateSubmodule: true,
    dockerImage: 'build-tools/android-build-box:jdk17',
    gpgFiles: ['app/google-services.json'],
    dojoProductType: 'sora'
    )
pipeline.runPipeline('sora')
