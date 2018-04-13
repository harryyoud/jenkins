import groovy.json.JsonSlurper

String getDevices() { new File("${workspace}@script/resources/lineage-targets.json").text }

def jsonParse(def json) { new groovy.json.JsonSlurperClassic().parseText(json) }

def global_repopick_nums = null
def global_repopick_tops = null

node("master"){
  try {
    def json = jsonParse(getDevices())
    for(int i = 0; i < json.size(); i++) {
      if(device) {
        if(device != json[i].device) {
          if(version != json[i].version) {
            continue
          }
          continue
        }
      }
      if(json[i].device == "GLOBAL") {
        global_repopick_nums = json[i].repopick_nums
        global_repopick_tops = json[i].repopick_tops
        continue
      }
      echo "Kicking off a build for ${json[i].device}"
      build job: 'lineage-build', parameters: [
        string(name: 'VERSION', value: (json[i].version == null) ? "14.1" : json[i].version),
        string(name: 'DEVICE', value: (json[i].device == null) ? "HELP-omgwtfbbq" : json[i].device),
        string(name: 'BUILD_TYPE', value: (json[i].build_type == null) ? "userdebug" : json[i].build_type),
        string(name: 'REPOPICK_NUMBERS', value: (json[i].repopick_nums == null) ? "" : json[i].repopick_nums),
        string(name: 'REPOPICK_TOPICS', value: (json[i].repopick_tops == null) ? "" : json[i].repopick_tops),
        string(name: 'GLOBAL_REPOPICK_NUMBERS', value: (global_repopick_nums == null) ? "" : global_repopick_nums),
        string(name: 'GLOBAL_REPOPICK_TOPICS', value: (global_repopick_tops == null) ? "" : global_repopick_tops),
        string(name: 'OTA', value: (json[i].ota == null) ? "true" : json[i].ota),
        string(name: 'CRON_RUN', value: 'true')
      ], propagate: false, wait: false
      sleep 2
    }
  } catch (e) {
    currentBuild.result = "FAILED"
    throw e
  }
}
