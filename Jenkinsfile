@Library('jenkins-library@65079bbe356bca4a3d5a1964e360498735afa1f0') _

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(steps: this,
    sonar: true,
    sonarProjectName: 'sora-passport-android',
    sonarProjectKey: 'jp.co.soramitsu:sora-passport-android',
    // Signed Play production builds come from .github/workflows/android_release.yml.
    // Jenkins runs the unit tests of both the develop and the production flavor.
    testCmd: 'ktlintCheck clean testDevelopDebugUnitTest testProductionDebugUnitTest koverVerifyDevelopDebug',
    publishType: 'Bundle',
    jobParams: jobParams,
    statusNotif: true,
    gitUpdateSubmodule: true,
    dockerImage: 'build-tools/android-build-box:jdk17',
    gpgFiles: ['app/google-services.json'],
    dojoProductType: 'sora-mobile')
pipeline.runPipeline('sora')
