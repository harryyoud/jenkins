node("build"){
	stage('Clone'){
		git url:'https://github.com/LineageOS/www'
	}
	stage('Pick commit'){
		sh '''#!/bin/bash
			until git ls-remote --exit-code origin refs/changes/${CHANGE: -2}/${CHANGE}/${PATCHSET} &>/dev/null ; do
				sleep 5
			done
			git fetch origin refs/changes/${CHANGE: -2}/${CHANGE}/${PATCHSET}
			git checkout FETCH_HEAD
		'''
	}
	stage('Go'){
		sh '''#!/bin/bash
			if [ $STATUS != NEW ]; then
				PRIVATE=private/
			fi
			sed -i s@baseurl:\\ \\"@baseurl:\\ \\"/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}@g _config.yml
			curl https://gist.githubusercontent.com/harryyoud/0977f6064d9c98ecab572e2b3c195f79/raw/073be2a5785d422eb7ac4331de7cb28c54e1aaad/gistfile1.txt > Dockerfile
			if ! docker image inspect lineageos/www > /dev/null; then
				docker build -t lineageos/www .
			fi
			echo >> _config.yml
			docker run --rm -e JEKYLL_ENV=$(git rev-parse --verify HEAD~1) -v $(pwd):/src lineageos/www
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=+1 -m \\'"PASS: MrRobot : ${BUILD_URL}console\nBuild successful for change $CHANGE, patchset $PATCHSET.\nPreview available at https://harryyoud.co.uk/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}"\\' $CHANGE,$PATCHSET
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
