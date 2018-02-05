def slack
node("master"){ slack = load "${workspace}@script/includes/slack-send.groovy" }

node("master"){
  stage('Clone'){
    git url:'https://github.com/LineageOS/mirror'
  }
  stage('Generate mirror'){
    withCredentials([string(credentialsId: '6011576d-29fd-4457-9b00-5c4b153822ef', variable: 'GHTOKEN')]) {
      sh '''#!/bin/bash +x
        mv default.xml old-default.xml
        export GHUSER="harryyoud"
        python3 ./mirror-regen.py
      '''
    }
  }
  stage('Compare'){
    sh '''#!/bin/bash +x
      diff default.xml old-default.xml
      if [ $? = 1 ]; then
        echo "New manifest differs from previous"
      fi
    '''
  }
  stage('Create draft change'){
    sh '''#!/bin/bash +x
      diff default.xml old-default.xml > /dev/null
      if [ $? = 1 ]; then
        git add default.xml
        gitdir=$(git rev-parse --git-dir); scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg ${gitdir}/hooks/
        git commit -m "Updated to $(printf "%(%d-%b-%Y)T\\n" -1) $(date -u +%H:%M:%S) UTC" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
        git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/mirror HEAD:refs/for/master
      else
        echo "No changes, skipping"
      fi
    '''
  }
  post {
    always {
      slack.notifySlack(currentBuild.result)
      cleanWs()
    }
  }
}
