@Library('jenkins-library') _

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(steps: this,
    sonar: true,
    sonarProjectName: 'sora-android',
    sonarProjectKey: 'sora:sora-android',
    testCmd: 'ktlintCheck clean testDevelopDebugUnitTest koverVerifyDevelopDebug',
    publishType: 'Bundle',
    jobParams: jobParams,
    appPushNoti: true,
    gitUpdateSubmodule: true,
    dockerImage: 'build-tools/android-build-box:jdk17',
    gpgFiles: ['app/google-services.json'])
pipeline.runPipeline('sora')
