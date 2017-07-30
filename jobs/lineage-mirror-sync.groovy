def slack
node("master"){ slack = load "${workspace}@script/includes/slack-send.groovy" }

node("the-revenge"){
  try {
    sh '''#!/bin/bash +x
      cd /mnt/Data/AndroidMirror/
      repo sync -j16
    '''
  } catch (e) {
    currentBuild.result = "FAILED"
    throw e
  } finally {
    slack.notifySlack(currentBuild.result)
  }
}
