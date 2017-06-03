node("the-revenge"){
    sh '''#!/bin/bash +x
        cd /mnt/Media/Lineage/
        repo sync -j16
    '''
}
