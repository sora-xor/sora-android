@Library('jenkins-library@feature/DOPS-2458/gpg' ) _

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(steps: this,
    sonar: true,
    sonarProjectName: 'sora-passport-android',
    sonarProjectKey: 'jp.co.soramitsu:sora-passport-android',
    testCmd: 'ktlint clean runModuleTests jacocoTestReport',
    jobParams: jobParams,
    appPushNoti: true,
    dockerImage: 'build-tools/android-build-box-jdk11:latest',
    gpgFiles: ['app/google-services.json', 'app/google-services-copy.json'])
pipeline.runPipeline('sora')
