node("master"){
  stage('Clone'){
    withCredentials([string(credentialsId: '6011576d-29fd-4457-9b00-5c4b153822ef', variable: 'GH_API_KEY')]) {
      sh '''#!/bin/bash +x
        set -e
        rm -rf cve_tracker lineageos_updater scripts
        git clone git@github.com:LineageOS/cve_tracker
        git clone git@github.com:lineageos/lineageos_updater
        git clone git@github.com:lineageos/scripts
        echo "$GH_API_KEY" > scripts/device-deps-regenerator/token
        scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg cve_tracker/.git/hooks/
        scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg lineageos_updater/.git/hooks/
      '''
    }
  }
  stage('Generate json'){
    sh '''#!/bin/bash +x
      set -e
      cd scripts/device-deps-regenerator
      python3 app.py
      python3 device2kernel.py
      python3 devices.py
    '''
  }
  stage('Create draft change'){
    sh '''#!/bin/bash +x
      diff scripts/device-deps-regenerator/kernels.json cve_tracker/kernels.json
      if [ $? = 1 ]; then
        cp scripts/device-deps-regenerator/kernels.json cve_tracker/kernels.json
        git -C cve_tracker add kernels.json
        git -C cve_tracker commit -m "Regenerate kernel->device mappings" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
        git -C cve_tracker push ssh://harryyoud@review.lineageos.org:29418/LineageOS/cve_tracker HEAD:refs/drafts/master
      else
        echo "No changes in cve_tracker, skipping"
      fi
      diff scripts/device-deps-regenerator/devices.json lineageos_updater/device_deps.json > /dev/null
      if [ $? = 1 ]; then
        cp scripts/device-deps-regenerator/devices.json lineageos_updater/device_deps.json
        git -C lineageos_updater add device_deps.json
        git -C lineageos_updater commit -m "Regenerate device dependency mappings" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
        git -C lineageos_updater push ssh://harryyoud@review.lineageos.org:29418/LineageOS/lineageos_updater HEAD:refs/drafts/master
      else
        echo "No changes in lineageos_updater, skipping"
      fi
    '''
  }
}
