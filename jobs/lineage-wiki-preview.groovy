node("build"){
	stage('Clone'){
			git url:'https://github.com/LineageOS/lineage_wiki'
	}
	stage('Pick commit'){
		sh '''#!/bin/bash
			sleep 60
			git fetch origin refs/changes/${CHANGE: -2}/${CHANGE}/${PATCHSET}
			git checkout FETCH_HEAD
                '''
	}
	stage('Go'){
		sh '''#!/bin/bash
			if [ $STATUS != NEW ]; then
				PRIVATE=private/
			fi
			curl https://gist.githubusercontent.com/harryyoud/0977f6064d9c98ecab572e2b3c195f79/raw/073be2a5785d422eb7ac4331de7cb28c54e1aaad/gistfile1.txt > Dockerfile
			if ! docker image inspect lineageos/lineage_wiki > /dev/null; then
				docker build -t lineageos/lineage_wiki .
			fi
			docker run --entrypoint test/validate.rb -tv $(pwd):/src -w /src lineageos/lineage_wiki
			if [ $? != 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nValidation failed for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
				exit 1
			fi
			echo >> _config.yml
			echo "baseurl: /lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}" >> _config.yml
			docker run -e JEKYLL_ENV=$(git rev-parse --verify HEAD) -v $(pwd):/src lineageos/lineage_wiki
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=+1 -m \\'"PASS: MrRobot : ${BUILD_URL}console\nBuild successful for change $CHANGE, patchset $PATCHSET; validation passed.\nPreview available at https://harryyoud.co.uk/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}"\\' $CHANGE,$PATCHSET
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nBuild failed for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
			fi
			mkdir -p /home/www/nginx/sites/harryyoud.co.uk/public_html/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}
			rsync -vr _site/ /home/www/nginx/sites/harryyoud.co.uk/public_html/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET} --delete --exclude .well-known
		'''
	}
	stage('Reset'){
		sh '''
			git reset --hard origin/master
		'''
	}
}
