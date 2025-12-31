import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    @Override
    Map<String, Object> checkUpdate(JSONObject manifest, JSONObject args) {
        // def response = "https://pan.baidu.com/disk/cmsdata?clienttype=0&app_id=250528&t=${System.currentTimeMillis()}&do=client".toURL().text

        def platform = manifest.platform as String

        def response = ScriptVars.HTTP_CLIENT.send(
                HttpRequest.newBuilder("https://pan.baidu.com/disk/cmsdata?clienttype=0&app_id=250528&t=${System.currentTimeMillis()}&do=client".toURI())
                        .header("user-agent", ScriptVars.USER_AGENT)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).body()

        def jsonData = new JsonSlurper().parseText(response)
        if (jsonData.errorno != 0) {
            return null
        }
        def data = switch (platform) {
            case 'windows' -> jsonData.guanjia
            case 'linux' -> jsonData.linux
            case 'mac' -> jsonData.mac
            case 'android' -> jsonData.android
            default -> null
        }
        if (data == null) {
            return null
        }
        def versionNew = data.version
        def matcher = versionNew =~ '[vV]([\\d.]+)'
        if (!matcher.find()) {
            return null
        }
        def version = matcher.group(1)

        def url = switch (platform) {
            case 'linux' -> [
                    '.rpm': data.url,
                    '.deb': data.url_1
            ]
            case 'mac' -> [
                    'x64'  : data.url,
                    'arm64': data.url_1
            ]
            default -> data.url
        }

        return [
                version: version,
                url    : url
        ]
    }

}