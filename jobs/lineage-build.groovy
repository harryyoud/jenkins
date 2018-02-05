String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}
String calcTimestamp() { ['date', '+%s'].execute().text.trim()}

def BUILD_TREE  = "/home/harry/lineage/15.1"
def CCACHE_DIR  = "/home/harry/.ccache"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE
def timestamp = calcTimestamp()

def slack
node("master"){ slack = load "${workspace}@script/includes/slack-send.groovy" }

node("build"){
  timestamps {
    if(OTA == 'true') {
      currentBuild.displayName = basejobname
    } else {
      currentBuild.displayName = basejobname + '-priv'
    }
    slack.notifySlack('STARTED')
    stage('Input manifest'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        rm -rf .repo/local_manifests
        mkdir .repo/local_manifests
        curl --silent "https://raw.githubusercontent.com/harryyoud/jenkins/master/resources/manifest-lineage-15.1.xml" > .repo/local_manifests/roomservice.xml
      '''
    }
    stage('Sync'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        repo forall -c "git reset --hard"
        repo forall -c "git clean -f -d"
        repo sync -d -c -j128 --force-sync
        repo forall -c "git reset --hard"
        repo forall -c "git clean -f -d"
      '''
    }
    stage('Output manifest'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        rm -r manifests
        mkdir -p manifests
        repo manifest -r -o manifests/$DEVICE-$(date +%Y%m%d)-manifest.xml
      '''
    }
    stage('Repopicks'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        . build/envsetup.sh
        if ! [ -z $GLOBAL_REPOPICK_NUMBERS ]; then
          for rpnum in ${GLOBAL_REPOPICK_NUMBERS//,/ }; do
            repopick -fr $rpnum
          done
        else
          echo "No global repopick numbers chosen"
        fi
        if ! [ -z $GLOBAL_REPOPICK_TOPICS ]; then
          for rptopic in ${GLOBAL_REPOPICK_TOPICS//,/ }; do
            repopick -frt $rptopic
          done
        else
          echo "No global repopick topics chosen"
        fi
        if ! [ -z $REPOPICK_NUMBERS ]; then
          for rpnum in ${REPOPICK_NUMBERS//,/ }; do
            repopick -fr $rpnum
          done
        else
          echo "No repopick numbers chosen"
        fi
        if ! [ -z $REPOPICK_TOPICS ]; then
          for rptopic in ${REPOPICK_TOPICS//,/ }; do
            repopick -frt $rptopic
          done
        else
          echo "No repopick topics chosen"
        fi
      '''
    }
    stage('Clean'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        make clean
      '''
    }
    stage('Build'){
      sh '''#!/bin/bash +e
        cd '''+BUILD_TREE+'''
        . build/envsetup.sh
        export USE_CCACHE=1
        export CCACHE_COMPRESS=1
        export CCACHE_DIR='''+CCACHE_DIR+'''
        rm vendor/opengapps/build/modules/{Tycho,GCS}/Android.mk
        lunch lineage_$DEVICE-$BUILD_TYPE
        ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
        rm -rf ~/.jack*
        ./prebuilts/sdk/tools/jack-admin install-server ./prebuilts/sdk/tools/jack-launcher.jar ./prebuilts/sdk/tools/jack-server-*.jar
        export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx6g"
        ./prebuilts/sdk/tools/jack-admin start-server
        mka bacon
        ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
      '''
    }
    stage('Upload to Jenkins'){
      sh '''#!/bin/bash
        set -e
        if ! [[ $OTA = 'true' ]]; then
          cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/lineage-$VERSION-* .
        fi
        cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/installed-files.txt .
        cp '''+BUILD_TREE+'''/manifests/$DEVICE-$(date +%Y%m%d)-manifest.xml .
      '''
      archiveArtifacts artifacts: '*'
      sh '''#!/bin/bash
        rm *
      '''
    }
    stage('Upload to www'){
      sh '''#!/bin/bash
        if [ $OTA = 'true' ]; then
          zipname=$(find '''+BUILD_TREE+'''/out/target/product/$DEVICE/ -name 'lineage-15.1-*.zip' -type f -printf "%f\\n")
          rsync '''+BUILD_TREE+'''/out/target/product/$DEVICE/$zipname /home/www/nginx/sites/harryyoud.co.uk/ota/builds/
        else
          echo "Skipping as this is not a production build. Artifacts will be available in Jenkins"
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
}
