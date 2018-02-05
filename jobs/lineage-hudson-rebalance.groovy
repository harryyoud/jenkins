def slack
node("master"){ slack = load "${workspace}@script/includes/slack-send.groovy" }

node("master"){
  stage('Clone'){
    sh '''#!/bin/bash +x
      set -e
      rm -rf jenkins hudson hudson2 hudson-rebalance.py
      git clone https://github.com/LineageOS/hudson jenkins
      cp jenkins/lineage-build-targets hudson
      curl https://gist.githubusercontent.com/harryyoud/b7071f5ed4592a0a00f4af0c75db2ff6/raw > hudson-rebalance.py
    '''
  }
  stage('Rebalance'){
    sh '''#!/bin/bash +x
      set -e
      python3 hudson-rebalance.py > out
    '''
  }
  stage('Create change'){
    sh '''#!/bin/bash +x
      diff hudson hudson2
      if [ $? = 1 ]; then
        cp hudson2 jenkins/lineage-build-targets
        git -C jenkins add lineage-build-targets
        scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg jenkins/.git/hooks/
        git -C jenkins commit -m "Rebalance hudson targets $(cat out)" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
        git -C jenkins push ssh://harryyoud@review.lineageos.org:29418/LineageOS/hudson HEAD:refs/drafts/master
      else
        echo "No changes in hudson, skipping"
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
