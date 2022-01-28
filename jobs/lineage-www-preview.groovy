node("built-in"){
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
			sed -i s@baseurl:\\ \\"@baseurl:\\ \\"/${PRIVATE}${CHANGE}/${PATCHSET}@g _config.yml
			sed -i s@url:\\ \\"@url:\\ \\"https://lineage.harryyoud.co.uk@g _config.yml
			curl https://gist.githubusercontent.com/harryyoud/0977f6064d9c98ecab572e2b3c195f79/raw/e979dc1f9c154bad61183708859740fb10c8070b/gistfile1.txt > Dockerfile
			echo >> _config.yml

			image_ver=$(git log -1 --pretty=%h -- Gemfile.lock)-$(git log -1 --pretty=%h -- Gemfile)
			docker build -t lineageos/www:$image_ver .

			docker run --rm -e JEKYLL_ENV=$(git rev-parse --verify HEAD~1) -v $(pwd):/src lineageos/www:$image_ver
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=+1 -m \\'"PASS: MrRobot : ${BUILD_URL}console\nBuild successful for change $CHANGE, patchset $PATCHSET.\nPreview available at https://lineage.harryyoud.co.uk/${PRIVATE}${CHANGE}/${PATCHSET}"\\' $CHANGE,$PATCHSET
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nBuild failed for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
			fi
			mkdir -p /var/www/lineage.harryyoud.co.uk/public/${PRIVATE}${CHANGE}/${PATCHSET}
			rsync -rh _site/ /var/www/lineage.harryyoud.co.uk/public/${PRIVATE}${CHANGE}/${PATCHSET} --delete --exclude .well-known
		'''
	}
	stage('Reset'){
		sh '''
			git reset --hard origin/master
		'''
	}
}
