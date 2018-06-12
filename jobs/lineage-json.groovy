pipeline {
  agent {label 'build'}
  stages {
    stage('Clone'){
      steps {
        withCredentials([string(credentialsId: 'GitHubAPIKey', variable: 'GH_API_KEY')]) {
          sh '''#!/bin/bash +x
            set -e
            rm -rf cve_tracker hudson scripts
            git clone git@github.com:lineageos/cve_tracker
            git clone git@github.com:lineageos/hudson
            git clone git@github.com:lineageos/scripts
            echo "$GH_API_KEY" > scripts/device-deps-regenerator/token
            scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg cve_tracker/.git/hooks/
            scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg hudson/.git/hooks/
          '''
        }
      }
    }
    stage('Generate json'){
      steps {
        sh '''#!/bin/bash +x
          set -e
          cd scripts/device-deps-regenerator
          python3 app.py
          python3 device2kernel.py
          python3 devices.py
        '''
      }
    }
    stage('Create draft change'){
      steps {
        sh '''#!/bin/bash +x
          diff scripts/device-deps-regenerator/kernels.json cve_tracker/kernels.json
          if [ $? = 1 ]; then
            cp scripts/device-deps-regenerator/kernels.json cve_tracker/kernels.json
            git -C cve_tracker add kernels.json
            git -C cve_tracker commit -m "Regenerate kernel->device mappings" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
            git -C cve_tracker push ssh://harryyoud@review.lineageos.org:29418/LineageOS/cve_tracker HEAD:refs/for/master
          else
            echo "No changes in cve_tracker, skipping"
          fi
          diff scripts/device-deps-regenerator/devices.json hudson/updater/device_deps.json > /dev/null
          if [ $? = 1 ]; then
            cp scripts/device-deps-regenerator/devices.json hudson/updater/device_deps.json
            git -C hudson add updater/device_deps.json
            git -C hudson commit -m "Regenerate device dependency mappings" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
            git -C hudson push ssh://harryyoud@review.lineageos.org:29418/LineageOS/hudson HEAD:refs/for/master
          else
            echo "No changes in hudson, skipping"
          fi
        '''
      }
    }
  }
  post {
    always {
      notifySlack(currentBuild.result)
    }
  }
}
