@Library('jenkins-library' ) _

def pipeline = new org.android.AppPipeline(steps: this, sonar: false, testCmd: 'ktlint clean runModuleTests', dockerImage: 'build-tools/android-build-box-jdk11:latest')
pipeline.runPipeline('sora')
