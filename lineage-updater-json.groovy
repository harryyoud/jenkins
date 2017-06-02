node("master"){
    stage('Clone'){
        sh '''#!/bin/bash +x
            set -e
            rm -rf lineageos_updater
            rm -rf scripts
            git clone git@github.com:LineageOS/lineageos_updater
            git clone git@github.com:lineageos/scripts
            echo "$GH_API_TOKEN" 
            #> scripts/device-deps-regenerator/token
            scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg lineageos_updater/.git/hooks/
        '''
    }
    stage('Generate json'){
        sh '''#!/bin/bash +x
            set -e
            cd scripts/device-deps-regenerator
            python3 app.py
            python3 devices.py
        '''
    }
    stage('Compare'){
        sh '''#!/bin/bash +x
            set +e
            diff scripts/device-deps-regenerator/devices.json lineageos_updater/device_deps.json
            if [ $? = 1 ]; then
                echo "New json differs from previous"
            else
                exit 0
            fi
        '''
    }
    stage('Create draft change'){
        sh '''#!/bin/bash +x
            diff scripts/device-deps-regenerator/devices.json lineageos_updater/device_deps.json > /dev/null
            if [ $? = 1 ]; then
                cp scripts/device-deps-regenerator/devices.json lineageos_updater/device_deps.json
                cd lineageos_updater
                git add device_deps.json
                git commit -m "Regenerate device dependency mappings" --author "Harry's Buildbot <buildbot@harryyoud.co.uk>"
                git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/lineageos_updater HEAD:refs/drafts/master
            else
                echo "No changes, skipping"
            fi
        '''
    }
}
