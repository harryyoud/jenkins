node("master"){
	stage('Clone'){
			git url:'https://github.com/LineageOS/hudson'
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
			if ! git diff-tree --no-commit-id --name-only -r master FETCH_HEAD | grep -qP "updater/(devices|device_deps).json"; then
				echo "Neither JSON file has been modified, skipping validation"
				exit
			fi

			jq -e . >/dev/null 2>&1 < updater/devices.json
			dev_res=$?
			jq -e . >/dev/null 2>&1 < updater/device_deps.json
			dep_res=$?

			msg=''
			fail=false

			if [ $dev_res -ne 0 ]; then
				msg=$'JSON validation failed on updater/devices.json\n'
				fail=true
			fi
			if [ $dep_res -ne 0 ]; then
				msg=${msg}$'JSON validation failed on updater/device_deps.json\n'
				fail=true
			fi

			if [ $fail = true ]; then
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=-1 -m \\'"FAIL: MrRobot : ${BUILD_URL}console\nJSON validation failed for change $CHANGE, patchset $PATCHSET\n\n$msg"\\' $CHANGE,$PATCHSET
				exit 1
			else
				ssh -p 29418 harry-jenkins@review.lineageos.org gerrit review -n OWNER --tag MrRobot --label Verified=+1 -m \\'"PASS: MrRobot : ${BUILD_URL}console\nJSON validation successful for change $CHANGE, patchset $PATCHSET"\\' $CHANGE,$PATCHSET
				exit 0
			fi
		'''
	}
	stage('Reset'){
		sh '''
			git reset --hard origin/master
		'''
	}
}
