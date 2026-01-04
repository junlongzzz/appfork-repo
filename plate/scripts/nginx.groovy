import com.alibaba.fastjson2.JSONObject
import org.dom4j.DocumentHelper
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        final def url = [:]
        final def versions = []

        def response = ScriptVars.HTTP_CLIENT.send(HttpRequest.newBuilder()
                .uri('https://raw.githubusercontent.com/nginx/nginx.org/refs/heads/main/xml/versions.xml'.toURI())
                .GET().build(), HttpResponse.BodyHandlers.ofString()).body()
        def document = DocumentHelper.parseText(response)

        for (channel in ['mainline', 'stable']) {
            def node = document.selectSingleNode("/versions/download[@tag='${channel}']/item[1]/@ver" as String)
            if (node != null) {
                def version = node.getText()
                versions << version
                url["nginx-${version}.zip (${channel})".toString()] = "https://nginx.org/download/nginx-${version}.zip".toString()
            }
        }

        return [
                version: String.join('/', versions),
                url    : url
        ]
    }

}