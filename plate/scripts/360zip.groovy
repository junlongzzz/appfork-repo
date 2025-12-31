import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def response = ScriptVars.HTTP_CLIENT.send(
                HttpRequest.newBuilder('https://yasuo.360.cn/update/versioninfo.json'.toURI())
                        .header('User-Agent', ScriptVars.USER_AGENT)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).body()
        def jsonData = new JsonSlurper().parseText(response)
        if (jsonData.errno != 0) {
            return null
        }

        return switch (manifest.platform) {
            case 'windows' -> [
                    'version': jsonData.data.winCode,
                    'url'    : jsonData.data.winDownloadUrl
            ]
            case 'mac' -> [
                    'version': jsonData.data.macCode,
                    'url'    : jsonData.data.macDownloadUrl
            ]
            default -> null
        }
    }

}