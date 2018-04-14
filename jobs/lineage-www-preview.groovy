node("build"){
	stage('Clone'){
		git url:'https://github.com/LineageOS/www'
	}
	stage('Pick commit'){
		withCredentials([string(credentialsId: 'LineageGerritHTTPPass', variable: 'GERRITHTTPPASS')]) {
			sh '''
				sleep 60
				python - $CHANGE $GERRITHTTPPASS<<-END
				from pygerrit2.rest import GerritRestAPI
				from sys import argv
				from os import system
				from requests.auth import HTTPBasicAuth
				gerrit_url = "https://review.lineageos.org/"
				auth = HTTPBasicAuth('harryyoud', argv[2])
				rest = GerritRestAPI(url=gerrit_url, auth=auth)
				change = rest.get("/changes/{}?o=DOWNLOAD_COMMANDS&o=CURRENT_REVISION".format(argv[1]))
				commands = change['revisions'].items()[0][1]['fetch'].itervalues().next()
				ref = commands['ref']
				rest.post("/changes/{}/reviewers".format(argv[1]), json={"reviewer": "harry-jenkins", "notify": "NONE"})
				system('git fetch https://github.com/LineageOS/www {} && git checkout FETCH_HEAD'.format(ref))
				END
			'''
		}
	}
	stage('Go'){
		sh '''#!/bin/bash
			if [ $STATUS == DRAFT ]; then
				PRIVATE=private/
			fi
			sed -i s@baseurl:\\ \\"@baseurl:\\ \\"/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}@g _config.yml
			curl https://gist.githubusercontent.com/harryyoud/0977f6064d9c98ecab572e2b3c195f79/raw/073be2a5785d422eb7ac4331de7cb28c54e1aaad/gistfile1.txt > Dockerfile
			if ! docker image inspect lineageos/www > /dev/null; then
				docker build -t lineageos/www .
			fi
			echo >> _config.yml
			docker run -e JEKYLL_ENV=$(git rev-parse --verify HEAD~1) -v $(pwd):/src lineageos/www
			if [ $? == 0 ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Code-Review=+1 -m \\'"Build successful for change $CHANGE, patchset $PATCHSET. Preview available at https://harryyoud.co.uk/lineage-previews/${PRIVATE}${CHANGE}/${PATCHSET}"\\' $CHANGE,$PATCHSET
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Code-Review=-1 -m \\'"Build failed for change $CHANGE, patchset $PATCHSET. View the log at ${BUILD_URL}console"\\' $CHANGE,$PATCHSET
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
