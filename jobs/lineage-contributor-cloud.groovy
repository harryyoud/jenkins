node("master"){
timestamps {
    stage('Pull'){
        sh '''#!/bin/bash +x
            set -e
            # Don't delete this folder, as it contains all the repos
            # downloaded by the script, which take a lot of time to download
            # TODO: symlink repo directory to ..
            if ! [ -d contributors-cloud-generator ]; then
                git clone git@github.com:LineageOS/contributors-cloud-generator
            fi
            cd contributors-cloud-generator
            git fetch origin
            git reset --hard origin/master
            rm -rf ../android_packages_apps_CMParts
            rm -rf ../android_packages_apps_Settings
            git clone -b cm-14.1 https://github.com/LineageOS/android_packages_apps_CMParts ../android_packages_apps_CMParts
            git clone -b cm-13.0 https://github.com/LineageOS/android_packages_apps_Settings ../android_packages_apps_Settings
            cd ..
        '''
    }
    stage('Build prerequisites'){
        sh '''#!/bin/bash +x
            set -e
            cd contributors-cloud-generator/source
            mvn package
            cp target/contributors-cloud-generator-1.0.jar ../lib
        '''
    }
    stage('Cleanup'){
        sh '''#!/bin/bash
            rm -rf contributors-cloud-generator/out
        '''
    }
    stage('Build cloud'){
        sh '''#!/bin/bash +x
            set -e
            cd contributors-cloud-generator
            ./generate_wordcloud.sh
        '''
    }
    stage('Create draft changes'){
        sh '''#!/bin/bash
            set -e
            cd android_packages_apps_CMParts
            cp ../contributors-cloud-generator/out/cloud.db assets/contributors.db
            git add assets/contributors.db
            scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg .git/hooks/
            git commit -m "Regenerate contributors cloud" --author "Harry Youd <harry@harryyoud.co.uk>"
            git commit --amend --no-edit
            git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/android_packages_apps_CMParts HEAD:refs/drafts/cm-14.1
            cd ../android_packages_apps_Settings
            cp ../contributors-cloud-generator/out/cloud.db assets/contributors.db
            git add assets/contributors.db
            scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg .git/hooks/
            git commit -m "Regenerate contributors cloud" --author "Harry Youd <harry@harryyoud.co.uk>"
            git commit --amend --no-edit
            git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/android_packages_apps_Settings HEAD:refs/drafts/cm-13.0
        '''
    }
    stage('Archive output'){
        archiveArtifacts artifacts: 'contributors-cloud-generator/out/cloud*'
    }
}
}
