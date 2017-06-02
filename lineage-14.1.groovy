String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}

node("the-revenge"){
timestamps {
    if (OTA == 'true') {
        currentBuild.displayName = DEVICE + '-' + calcDate() + '-' + BUILD_TYPE
    } else {
        currentBuild.displayName = DEVICE + '-' + calcDate() + '-' + BUILD_TYPE + '-priv'
    }
    if(BOOT_IMG_ONLY == 'true') {
        OTA = false
    }
    stage('Sync mirror'){
        sh '''#!/bin/bash
            if [ $CRON_RUN = 'true' ]; then
                echo "Automatic run, so mirror sync has already been done"
            else
                cd /mnt/Media/Lineage
                repo sync -j32
            fi
        '''
    }
    stage('Input manifest'){
        sh '''#!/bin/bash +x
            cd /mnt/Android/lineage/14.1
            rm -rf .repo/local_manifests
            mkdir .repo/local_manifests
            curl --silent "https://raw.githubusercontent.com/harryyoud/jenkins/master/manifest.xml" > .repo/local_manifests/roomservice.xml
        '''
    }
    stage('Sync'){
        sh '''#!/bin/bash
            set +x
            cd /mnt/Android/lineage/14.1
            repo sync -c -j64 --force-sync
            . build/envsetup.sh
            breakfast $DEVICE
        '''
    }
    stage('Output manifest'){
        sh '''#!/bin/bash
            set +x
            cd /mnt/Android/lineage/14.1
            rm -r manifests
            mkdir -p manifests
            repo manifest -r -o manifests/$DEVICE-$(date +%Y%m%d)-manifest.xml
        '''
    }
    stage('Repopicks'){
        sh '''#!/bin/bash
            cd /mnt/Android/lineage/14.1
            . build/envsetup.sh
            if ! [ -z $REPOPICK_NUMBERS ]; then
                for rpnum in ${REPOPICK_NUMBERS//,/ }; do
                    repopick -fg ssh://harryyoud@review.lineageos.org:29418 $rpnum
                done
            else
                echo "No repopick numbers chosen"
            fi
            if ! [ -z $REPOPICK_TOPICS ]; then
                for rptopic in ${REPOPICK_TOPICS//,/ }; do
                    repopick -fg ssh://harryyoud@review.lineageos.org:29418 -t $rptopic
                done
            else
                echo "No repopick topics chosen"
            fi
        '''
    }
    stage('Clean'){
        sh '''#!/bin/bash
            set +x
            cd /mnt/Android/lineage/14.1
            make clean
        '''
    }
    stage('Build'){
        sh '''#!/bin/bash
            set +x
            set -e
            cd /mnt/Android/lineage/14.1
            . build/envsetup.sh
            export USE_CCACHE=1
            export CCACHE_COMPRESS=1
            export CCACHE_DIR=/mnt/Android/ccache
            lunch lineage_$DEVICE-$BUILD_TYPE
            ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
            export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx6g"
            ./prebuilts/sdk/tools/jack-admin start-server
            if [ $BOOT_IMG_ONLY = 'true' ]; then
                mka bootimage
            else
                mka bacon
            fi
            ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
        '''
    }
    stage('Upload to Jenkins'){
        sh '''#!/bin/bash
            set +x
            set -e
            if ! [[ $OTA = 'true' || $BOOT_IMG_ONLY = 'true' ]]; then
                cp /mnt/Android/lineage/14.1/out/target/product/$DEVICE/lineage-14.1-* .
            fi
            if [ $BOOT_IMG_ONLY = 'true' ]; then
                cp /mnt/Android/lineage/14.1/out/target/product/$DEVICE/boot.img .
            fi
            cp /mnt/Android/lineage/14.1/manifests/$DEVICE-$(date +%Y%m%d)-manifest.xml .
            cp /mnt/Android/lineage/14.1/out/target/product/$DEVICE/installed-files.txt .
        '''
        archiveArtifacts artifacts: '*'
        sh '''#!/bin/bash
            set +x
            rm *
        '''
    }
    stage('Upload to www'){
        sh '''#!/bin/bash
            set +x
            if [ $OTA = 'true' ]; then
                scp /mnt/Android/lineage/14.1/out/target/product/$DEVICE/lineage-14.1-* root@builder.harryyoud.co.uk:/srv/www/builder.harryyoud.co.uk/lineage/
            else
                echo "Skipping as this is not a production build. Artifacts will be available in Jenkins"
            fi
        '''
    }
    stage('Add to updater'){
        sh '''#!/bin/bash
            set +x
            cd /mnt/Android/lineage/14.1/out/target/product/$DEVICE
            if [ $OTA = 'true' ]; then
                newname=$(find -name 'lineage-14.1-*.zip' -printf '%f\\n')
                md5sum=$(cat lineage-14.1-*.zip.md5sum)
                curl -H "Apikey: $UPDATER_API_KEY" -H "Content-Type: application/json" -X POST -d '{ "device": "'"$DEVICE"'", "filename": "'"$newname"'", "md5sum": "'"${md5sum:0:32}"'", "romtype": "unofficial", "url": "'"http://builder.harryyoud.co.uk/lineage/$newname"'", "version": "'"14.1"'" }' "https://lineage.harryyoud.co.uk/api/v1/add_build"
            fi
        '''
    }
}
}
