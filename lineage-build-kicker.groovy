import groovy.json.JsonSlurper
String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/harryyoud/jenkins/master/lineage-targets.json'].execute().text }

def jsonParse(def json) { new groovy.json.JsonSlurperClassic().parseText(json) }

node("master"){
	build job: 'lineage-mirror-sync', propogate: true, wait: true
	def json = jsonParse(getDevices())
	for(int i = 0; i < json.size(); i++) {
		if(device) {
			if(device != json[i].device) {
				continue
			}
		}
		echo "Kicking off a build for ${json[i].device}"
		build job: 'lineage-14.1', parameters: [
			string(name: 'DEVICE', value: (json[i].device == null) ? "HELP-omgwtfbbq" : json[i].device),
			string(name: 'BUILD_TYPE', value: (json[i].build_type == null) ? "userdebug" : json[i].build_type),
			string(name: 'REPOPICK_NUMBERS', value: (json[i].repopick_nums == null) ? "" : json[i].repopick_nums),
			string(name: 'REPOPICK_TOPICS', value: (json[i].repopick_tops == null) ? "" : json[i].repopick_tops),
			string(name: 'WITH_SU', value: (json[i].with_su == null) ? "false" : json[i].with_su),
			string(name: 'WITH_GAPPS', value: (json[i].with_gapps == null) ? "false" : json[i].with_gapps),
			string(name: 'WITH_DEXPREOPT', value: (json[i].with_dexpreopt == null) ? "false" : json[i].with_dexpreopt),
			string(name: 'OTA', value: (json[i].ota == null) ? "true" : json[i].ota),
			string(name: 'SIGNED', value: (json[i].signed == null) ? "false" : json[i].signed),
			string(name: 'CRON_RUN', value: 'true')
		], propagate: false, wait: false
		sleep 2
	}
}
