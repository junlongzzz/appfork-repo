import com.alibaba.fastjson2.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    @Override
    Map<String, Object> checkUpdate(JSONObject manifest, JSONObject args) {
        def request = HttpRequest.newBuilder('https://www.virtualbox.org/wiki/Downloads'.toURI())
                .header('User-Agent', ScriptVars.USER_AGENT).GET().build()
        def response = ScriptVars.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        def document = Jsoup.parse(response.body())
        if (!document) {
            return null
        }

        def version = null
        def url = []
        def elements = document.select('ul a.ext-link[href], .license-button[href]')
        for (Element element : elements) {
            def href = element.attr('href')
            if (href.endsWith('.exe')) {
                def matcher = href =~ '/(?<version>[\\d.]+)/'
                if (!matcher.find()) {
                    return null
                }
                version = matcher.group('version')
                url << href
            } else if (href.endsWith('.vbox-extpack')) {
                url << href
            }
        }

        return [
                'version': version,
                'url'    : url
        ]
    }

}