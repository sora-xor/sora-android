@Library('jenkins-library@feature/DOPS-2957' ) _

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(steps: this,
    sonar: true,
    sonarProjectName: 'sora-passport-android',
    sonarProjectKey: 'jp.co.soramitsu:sora-passport-android',
    testCmd: 'ktlintCheck clean testDevelopDebugUnitTest koverVerifyDevelopDebug',
    publishType: 'Bundle',
    jobParams: jobParams,
    appPushNoti: true,
    gitUpdateSubmodule: true,
    dockerImage: 'build-tools/android-build-box:jdk17',
    gpgFiles: ['app/google-services.json'],
    dojoProductType: 'sora-mobile'
    )
pipeline.runPipeline('sora')
