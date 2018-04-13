String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}
String calcTimestamp() { ['date', '+%s'].execute().text.trim()}

def BUILD_TREE  = "/home/harry/lineage/15.1"
def CCACHE_DIR  = "/home/harry/.ccache"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE
def timestamp = calcTimestamp()

node("build"){
  timestamps {
    currentBuild.displayName = basejobname
    if(OTA != 'true') {
      currentBuild.displayName = basejobname + '-priv'
    }
    stage('Sync'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        repo forall -c "git reset --hard"
        repo forall -c "git clean -f -d"
        repo sync -d -c -j128 --force-sync
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
            repopick -r $rpnum
          done
        else
          echo "No global repopick numbers chosen"
        fi
        if ! [ -z $GLOBAL_REPOPICK_TOPICS ]; then
          for rptopic in ${GLOBAL_REPOPICK_TOPICS//,/ }; do
            repopick -rt $rptopic
          done
        else
          echo "No global repopick topics chosen"
        fi
        if ! [ -z $REPOPICK_NUMBERS ]; then
          for rpnum in ${REPOPICK_NUMBERS//,/ }; do
            repopick -r $rpnum
          done
        else
          echo "No repopick numbers chosen"
        fi
        if ! [ -z $REPOPICK_TOPICS ]; then
          for rptopic in ${REPOPICK_TOPICS//,/ }; do
            repopick -rt $rptopic
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
        export ANDROID_COMPILE_WITH_JACK=false
        lunch lineage_$DEVICE-$BUILD_TYPE
        mka target-files-package otatools dist
        export DATE=$(date -u +%Y%m%d)
        if [ -f tfgp/$DEVICE.zip ]; then
          ./build/tools/releasetools/ota_from_target_files -i tfgp/$DEVICE.zip out/dist/*-target_files-*.zip out/dist/lineage-$VERSION-$DATE-UNOFFICIAL-$DEVICE.zip
          cp out/dist/*-target_files-*.zip tfgp/$DEVICE.zip
        else
          ./build/tools/releasetools/ota_from_target_files out/dist/*-target_files-*.zip out/dist/lineage-$VERSION-$DATE-UNOFFICIAL-$DEVICE.zip
        fi
      '''
    }
    stage('Upload to Jenkins'){
      sh '''#!/bin/bash
        set -e
        if ! [[ $OTA = 'true' ]]; then
          cp '''+BUILD_TREE+'''/out/dist/lineage-$VERSION-* .
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
          zipname=$(find '''+BUILD_TREE+'''/out/dist/ -name 'lineage-15.1-*.zip' -type f -printf "%f\\n")
          rsync '''+BUILD_TREE+'''/out/dist/$zipname /home/www/nginx/sites/harryyoud.co.uk/ota/builds/
        else
          echo "Skipping as this is not a production build. Artifacts will be available in Jenkins"
        fi
      '''
    }
  }
}
