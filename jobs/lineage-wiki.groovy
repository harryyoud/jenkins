node("master"){
	stage('Clone'){
			git url:'https://github.com/LineageOS/lineage_wiki'
	}
	stage('Pick commit'){
		sh '''
			python - $CHANGE <<-END
				from pygerrit2.rest import GerritRestAPI
				from sys import argv
				from os import system
				gerrit_url = "https://review.lineageos.org/"
				rest = GerritRestAPI(url=gerrit_url)
				change = rest.get("/changes/{}?o=DOWNLOAD_COMMANDS&o=CURRENT_REVISION".format(argv[1]))
				commands = change['revisions'].items()[0][1]['fetch']['anonymous http']
				url = commands['url']
				ref = commands['ref']
				system('git fetch {} {} && git checkout FETCH_HEAD'.format(url, ref))
				END
			gpick 203329
		'''
	}
	stage('Go'){
		sh '''#!/bin/bash
			curl https://gist.githubusercontent.com/harryyoud/0977f6064d9c98ecab572e2b3c195f79/raw/073be2a5785d422eb7ac4331de7cb28c54e1aaad/gistfile1.txt > Dockerfile
			if ! docker image inspect lineageos/lineage_wiki > /dev/null; then
				docker build -t lineageos/lineage_wiki .
			fi
			docker run --entrypoint test/validate.rb -tv $(pwd):/src -w /src lineageos/lineage_wiki
			if [ $? != 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --label Verified=-1 -m \\'"Validation failed for change $CHANGE, patchset $PATCHSET. View the log at ${BUILD_URL}console"\\' $CHANGE,$PATCHSET
				exit 1
			fi
			echo >> _config.yml
			echo "baseurl: /lineage-previews/$CHANGE/$PATCHSET" >> _config.yml
			docker run -e JEKYLL_ENV=$(git rev-parse --verify HEAD~2) -v $(pwd):/src lineageos/lineage_wiki
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --label Verified=+1 -m \\'"Build successful for change $CHANGE, patchset $PATCHSET; validation passed. Preview available at https://harryyoud.co.uk/lineage-previews/$CHANGE/$PATCHSET"\\' $CHANGE,$PATCHSET
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER--label Verified=-1 -m \\'"Build failed for change $CHANGE, patchset $PATCHSET. View the log at ${BUILD_URL}console"\\' $CHANGE,$PATCHSET
			fi
			mkdir -p /home/harry/public_html/lineage-previews/$CHANGE/$PATCHSET
			rsync -vr _site/ /home/harry/public_html/lineage-previews/$CHANGE/$PATCHSET --delete --exclude .well-known --exclude images/devices
		'''
	}
	stage('Reset'){
		sh '''
			git reset --hard origin/master
		'''
	}
}
