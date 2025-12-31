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
                HttpRequest.newBuilder('https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/Prerelease-alpha/output-metadata.json'.toURI())
                        .header('User-Agent', ScriptVars.USER_AGENT)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).body()
        def jsonData = new JsonSlurper().parseText(response)

        def url = []
        def urlPrefix = 'https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/Prerelease-alpha/'
        jsonData.elements.each {
            url.add("${urlPrefix}${it.outputFile}" as String)
        }

        return [
                url: url.sort()
        ]
    }

}