import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Map<String, Object> checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String

        switch (platform) {
            case 'windows':
                platform = 'windows'
                break
            case 'linux':
                platform = 'linux'
                break
            case 'mac':
                platform = 'macos'
                break
            default:
                return null
        }
        // releases_windows releases_linux releases_macos
        def response = "https://storage.googleapis.com/flutter_infra_release/releases/releases_${platform}.json".toURL().text
//    def response = "https://storage.flutter-io.cn/flutter_infra_release/releases/releases_${platform}.json".toURL().text
        def jsonData = new JsonSlurper().parseText(response)
        def stableHash = jsonData.current_release.stable
        def version = null
        def url = []
        jsonData.releases.findAll { it.hash == stableHash }.each {
            version = it.version
            url << "${jsonData.base_url}/${it.archive}".toString()
        }

        return [
                'version': version,
                'url'    : url
        ]
    }

}