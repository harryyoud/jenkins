String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/harryyoud/jenkins/master/lineage-targets'].execute().text }
node("master"){
	def json = jsonParse(getDevices())
	json.each {
		echo "Building ${it.device}-${it.build_type}"
		build job: 'lineage-14.1', parameters: [
			string(name: 'DEVICE', value: (it.device == null) ? "" : it.device),
			string(name: 'BUILD_TYPE', value: (it.build_type == null) ? "" : it.build_type),
			string(name: 'OTA', value: 'true'),
			string(name: 'REPOPICK_NUMBERS', value: (it.repopick_nums == null) ? "" : it.repopick_nums),
			string(name: 'REPOPICK_TOPICS', value: (it.repopick_tops == null) ? "" : it.repopick_tops),
			string(name: 'WITH_SU', value: (it.with_su == null) ? "" : it.with_su),
			string(name: 'WITH_GAPPS', value: (it.with_gapps == null) ? "" : it.with_gapps),
			string(name: 'CRON_RUN', value: 'true')
		], propagate: false, wait: false
		sleep 2
	}
	json = null
