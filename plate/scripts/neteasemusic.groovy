import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def request = HttpRequest.newBuilder()
                .uri('https://music.163.com/api/appcustomconfig/get'.toURI())
                .header('User-Agent', ScriptVars.USER_AGENT)
                .GET()
                .build()
        def response = ScriptVars.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body()
        def data = new JsonSlurper().parseText(response)['data']
        if (!data) {
            return null
        }
        def url = data?['web-new-download']?['pc64']?['downloadUrl']
        if (!url) {
            return null
        }
        def matcher = url =~ '/*_([\\d.]+)'
        if (!matcher.find()) {
            return null
        }

        return [
                version: matcher.group(1),
                url    : url
        ]
    }

}