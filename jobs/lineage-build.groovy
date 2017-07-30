String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}
String calcTimestamp() { ['date', '+%s'].execute().text.trim()}

def MIRROR_TREE = "/mnt/Data/AndroidMirror"
def BUILD_TREE  = "/mnt/Android/lineage/" + VERSION
def CCACHE_DIR  = "/mnt/Android/ccache"
def CERTS_DIR   = "/mnt/Android/certs"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE
def timestamp = calcTimestamp()

def slack
node("master"){ slack = load "${workspace}@script/includes/slack-send.groovy" }

node("the-revenge"){
  timestamps {
    try {
      if(SIGNED == 'true') {
        basejobname = basejobname + '-signed'
      }
      if(OTA == 'true') {
        currentBuild.displayName = basejobname
      } else {
        currentBuild.displayName = basejobname + '-priv'
      }
      slack.notifySlack('STARTED')
      if(BOOT_IMG_ONLY == 'true') {
        OTA = false
      }
      if(VERSION == '11') {
        OTA = false
        WITH_DEXPREOPT = false
        WITH_GAPPS = false
        WITH_OMS = false
        WITH_SU = false
      }
      if(VERSION == '13.0') {
        WITH_GAPPS = false
        WITH_OMS = false
      }
      stage('Sync mirror'){
        sh '''#!/bin/bash
          if [ $CRON_RUN = 'true' ]; then
            echo "Automatic run, so mirror sync has already been done"
          else
            cd '''+MIRROR_TREE+'''
            repo sync -j32
          fi
        '''
      }
      stage('Input manifest'){
        sh '''#!/bin/bash
          cd '''+BUILD_TREE+'''
          rm -rf .repo/local_manifests
          mkdir .repo/local_manifests
          curl --silent "https://raw.githubusercontent.com/harryyoud/jenkins/master/resources/manifest-$VERSION.xml" > .repo/local_manifests/roomservice.xml
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
          . build/envsetup.sh
          breakfast $DEVICE
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
              repopick -frg ssh://harryyoud@review.lineageos.org:29418 $rpnum
            done
          else
            echo "No global repopick numbers chosen"
          fi
          if ! [ -z $GLOBAL_REPOPICK_TOPICS ]; then
            for rptopic in ${GLOBAL_REPOPICK_TOPICS//,/ }; do
              repopick -frg ssh://harryyoud@review.lineageos.org:29418 -t $rptopic
            done
          else
            echo "No global repopick topics chosen"
          fi
          if ! [ -z $REPOPICK_NUMBERS ]; then
            for rpnum in ${REPOPICK_NUMBERS//,/ }; do
              repopick -frg ssh://harryyoud@review.lineageos.org:29418 $rpnum
            done
          else
            echo "No repopick numbers chosen"
          fi
          if ! [ -z $REPOPICK_TOPICS ]; then
            for rptopic in ${REPOPICK_TOPICS//,/ }; do
              repopick -frg ssh://harryyoud@review.lineageos.org:29418 -t $rptopic
            done
          else
            echo "No repopick topics chosen"
          fi
          if [[ $WITH_OMS = 'true' ]]; then
            vendor/extra/patch.sh
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
          if [[ $VERSION = '11' ]]; then
            cd vendor/cm
            ./get-prebuilts
            cd ../..
            export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre/
            export JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
            export PATH=$JDK_HOME/bin:$JAVA_HOME:$PATH
          fi
          lunch lineage_$DEVICE-$BUILD_TYPE || lunch cm_$DEVICE-$BUILD_TYPE
          if [[ $VERSION = '14.1' ]]; then
            ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
            export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx6g"
            ./prebuilts/sdk/tools/jack-admin start-server
          fi
          if [ $BOOT_IMG_ONLY = 'true' ]; then
            mka bootimage
          else
            mka bacon
          fi
          if [[ $VERSION = '14.1' ]]; then
            ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
          fi
        '''
      }
      if(SIGNED == 'true'){
        stage('Sign build'){
          sh '''#!/bin/bash
            set -e
            cd '''+BUILD_TREE+'''
            OtaScriptPath=$([ -f out/target/product/$DEVICE/ota_script_path ] && cat "out/target/product/$DEVICE/ota_script_path" || echo "build/tools/releasetools/ota_from_target_files")
            rm -f out/target/product/$DEVICE/lineage-$VERSION-*.zip
            ./build/tools/releasetools/sign_target_files_apks -o -d '''+CERTS_DIR+''' \
              out/target/product/$DEVICE/obj/PACKAGING/target_files_intermediates/*target_files*.zip \
              out/target/product/$DEVICE/jenkins-signed-target_files.zip
            $OtaScriptPath -k '''+CERTS_DIR+'''/releasekey \
              --block --backup=$SIGNED_BACKUPTOOL \
              out/target/product/$DEVICE/jenkins-signed-target_files.zip \
              out/target/product/$DEVICE/lineage-$VERSION-$(date +%Y%m%d)-UNOFFICIAL-$DEVICE-signed.zip
          '''
        }
      }
      stage('Upload to Jenkins'){
        sh '''#!/bin/bash
          set -e
          if ! [[ $OTA = 'true' || $BOOT_IMG_ONLY = 'true' ]]; then
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/lineage-$VERSION-* .
          fi
          if [ $BOOT_IMG_ONLY = 'true' ]; then
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/boot.img .
          else
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/installed-files.txt .
          fi
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
            if [[ ! $VERSION = '11' ]]; then
              zipname=$(find '''+BUILD_TREE+'''/out/target/product/$DEVICE/ -name 'lineage-'$VERSION'-*.zip' -type f -printf "%f\\n")
              ssh root@builder.harryyoud.co.uk mkdir -p /srv/www/builder.harryyoud.co.uk/lineage/$DEVICE/'''+timestamp+'''/
              scp '''+BUILD_TREE+'''/out/target/product/$DEVICE/$zipname root@builder.harryyoud.co.uk:/srv/www/builder.harryyoud.co.uk/lineage/$DEVICE/'''+timestamp+'''/
            else
              echo "CM11 does not support OTAs using https://github.com/LineageOS/lineageos_updater"
            fi
          else
            echo "Skipping as this is not a production build. Artifacts will be available in Jenkins"
          fi
        '''
      }
      stage('Add to updater'){
        withCredentials([string(credentialsId: '3ad6afb4-1f2a-45e9-94c7-b2b511f81d50', variable: 'UPDATER_API_KEY')]) {
          sh '''#!/bin/bash
            cd '''+BUILD_TREE+'''/out/target/product/$DEVICE
            if [ $OTA = 'true' ]; then
              zipname=$(find -name "lineage-$VERSION-*.zip" -type f -printf '%f\n')
              md5sum=$(md5sum $zipname)
              curl -H "Apikey: $UPDATER_API_KEY" -H "Content-Type: application/json" -X POST -d '{ "device": "'"$DEVICE"'", "filename": "'"$zipname"'", "md5sum": "'"${md5sum:0:32}"'", "romtype": "unofficial", "url": "'"http://builder.harryyoud.co.uk/lineage/$DEVICE/'''+timestamp+'''/$zipname"'", "version": "'"14.1"'" }' "https://lineage.harryyoud.co.uk/api/v1/add_build"
            fi
          '''
        }
      }
    } catch (e) {
      currentBuild.result = "FAILED"
      throw e
    } finally {
      slack.notifySlack(currentBuild.result)
    }
  }
}
