node("the-revenge"){
    sh '''#!/bin/bash +x
        cd /mnt/Data/AndroidMirror/
        repo sync -j16
    '''
}
