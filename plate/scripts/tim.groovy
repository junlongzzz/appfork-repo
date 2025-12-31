import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def response = 'https://im.qq.com/rainbow/TIMDownload/'.toURL().text
        if (response == null || response.isEmpty()) {
            return null
        }
        def matcher = response.replace(' ', '') =~ 'params=(\\{.*})'
        if (!matcher.find()) {
            return null
        }
        def jsonData = new JsonSlurper().parseText(matcher.group(1))
        def download = jsonData.app.download
        return switch (manifest.platform) {
            case 'windows' -> [
                    version: download.pcVersion,
                    url    : download.pcLink
            ]
            case 'android' -> [
                    version: download.androidVersion,
                    url    : download.androidLink
            ]
            default -> null
        }
    }

}