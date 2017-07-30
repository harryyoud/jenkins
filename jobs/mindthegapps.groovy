String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/harryyoud/jenkins/master/resources/mindthegapps-targets'].execute().text }

node("master"){
  stage('Clone repository') {
    git url: 'https://github.com/MindTheGapps/vendor_gapps'
  }
  stage('Clean out directory') {
    sh '''
      set +x
      make distclean
    '''
  }
  def lines = getDevices().split("\n")
  for (int i = 0; i < lines.length; i++){
    line = lines[i].trim()
    if (line.startsWith("#") || line.equals("")) {
      continue
    }
    data = line.split(" ")
    def arch = data[0]
    stage("Build gapps for $arch") {
      echo "Building mindthegapps-${arch}"
      sh """
        set +x
        make gapps_$arch
      """
    }
  }
  stage('Upload artifacts') {
    sh '''
      set +x
      find out/ -type f -name MindTheGapps*.zip -exec /var/lib/jenkins/afh/afh-ftp.py 178688 {} \\;
    '''
    }
  }
