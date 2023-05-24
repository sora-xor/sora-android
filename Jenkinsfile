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
    gpgFiles: [[source: 'app/google-services.gpg', dest: 'app/google-services.json'], 
      [source: 'app/google-services-copy.gpg', dest: 'app/google-services-copy.json']])
pipeline.runPipeline('sora')
