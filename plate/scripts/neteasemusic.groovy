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
                .uri('https://music.163.com/api/appcustomconfig/get?key=web-pc-beta-download-links'.toURI())
                .header('User-Agent', ScriptVars.USER_AGENT)
                .GET()
                .build()
        def response = ScriptVars.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body()
        def matcher = response =~ '/*_([\\d.]+)'
        if (!matcher.find()) {
            return null
        }
        def respJson = new JsonSlurper().parseText(response)
        if (respJson.code != 200 || !respJson['data']) {
            return null
        }
        def links = respJson['data']['web-pc-beta-download-links']
        if (!links) {
            return null
        }

        return [
                version: matcher.group(1),
                url    : [
                        '64bit': links['pcPackage64'],
                        '32bit': links['pcPackage32']
                ]
        ]
    }

}