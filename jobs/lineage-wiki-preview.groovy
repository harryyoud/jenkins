node("built-in"){
	stage('Clone'){
			git url:'https://github.com/LineageOS/lineage_wiki'
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
			if [ "$STATUS" != NEW ]; then
				PRIVATE=private/
			fi
			image_ver=$(git log -1 --pretty=%h -- Gemfile.lock)-$(git log -1 --pretty=%h -- Gemfile)
			docker build -t lineageos/lineage_wiki:$image_ver .
			docker run --entrypoint test/validate.rb -tv $(pwd):/src -w /src lineageos/lineage_wiki:$image_ver
			if [ $? != 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nValidation failed for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
				exit 1
			fi
			echo >> _config.yml
			echo "baseurl: /${PRIVATE}${CHANGE}/${PATCHSET}" >> _config.yml
			docker run --rm --entrypoint bundle -e JEKYLL_ENV=$(git rev-parse --verify HEAD) -v $(pwd):/src lineageos/lineage_wiki:$image_ver exec jekyll build --future
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=+1 -m \\'"PASS: MrRobot : ${BUILD_URL}console\nBuild successful for change $CHANGE, patchset $PATCHSET; validation passed.\nPreview available at https://lineage.harryyoud.co.uk/${PRIVATE}${CHANGE}/${PATCHSET}"\\' $CHANGE,$PATCHSET
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nBuild failed for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
			fi
			mkdir -p /var/www/lineage.harryyoud.co.uk/public/${PRIVATE}${CHANGE}/${PATCHSET}
			rsync -vr _site/ /var/www/lineage.harryyoud.co.uk/public/${PRIVATE}${CHANGE}/${PATCHSET} --delete --exclude .well-known --exclude=images/devices/
		'''
	}
	stage('Reset'){
		sh '''
			git reset --hard origin/master
		'''
	}
}
