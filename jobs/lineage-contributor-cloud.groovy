pipeline {
  agent {label 'build'}
  stages {
    stage('Pull'){
      steps {
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
          curl https://github.com/LineageOS/contributors-cloud-generator/commit/6f3cea78f9f516ee9e654a6f166bd8e28f3de1b2.patch | git am
          rm -rf ../android_packages_apps_LineageParts
          rm -rf ../android_packages_apps_CMParts
          git clone -b lineage-15.0 https://github.com/LineageOS/android_packages_apps_LineageParts ../android_packages_apps_LineageParts
          git clone -b cm-14.1 https://github.com/LineageOS/android_packages_apps_CMParts ../android_packages_apps_CMParts
          cd ..
        '''
      }
    }
    stage('Build prerequisites'){
      steps {
        sh '''#!/bin/bash +x
          set -e
          cd contributors-cloud-generator/source
          JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn package
          cp target/contributors-cloud-generator-1.0.jar ../lib
        '''
      }
    }
    stage('Cleanup'){
      steps {
        sh '''#!/bin/bash
          rm -rf contributors-cloud-generator/out
        '''
      }
    }
    stage('Build cloud'){
      steps {
        sh '''#!/bin/bash +x
          set -e
          cd contributors-cloud-generator
          JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 python3 generate_wordcloud.py --mirror=/mirror
        '''
      }
    }
    stage('Create draft changes'){
      steps {
        sh '''#!/bin/bash
          set -e
          cd android_packages_apps_LineageParts
          cp ../contributors-cloud-generator/out/cloud.db assets/contributors.db
          git add assets/contributors.db
          scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg .git/hooks/
          git commit -m "Regenerate contributors cloud" --author "Harry Youd <harry@harryyoud.co.uk>"
          git commit --amend --no-edit
          git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/android_packages_apps_LineageParts HEAD:refs/drafts/lineage-15.1
          cd ../android_packages_apps_CMParts
          cp ../contributors-cloud-generator/out/cloud.db assets/contributors.db
          git add assets/contributors.db
          scp -p -P 29418 harryyoud@review.lineageos.org:hooks/commit-msg .git/hooks/
          git commit -m "Regenerate contributors cloud" --author "Harry Youd <harry@harryyoud.co.uk>"
          git commit --amend --no-edit
          git push ssh://harryyoud@review.lineageos.org:29418/LineageOS/android_packages_apps_CMParts HEAD:refs/drafts/cm-14.1
        '''
      }
    }
    stage('Archive output'){
      steps {
        archiveArtifacts artifacts: 'contributors-cloud-generator/out/cloud*'
      }
    }
  }
  post {
    always {
      notifySlack(currentBuild.result)
      cleanWs()
    }
  }
}
