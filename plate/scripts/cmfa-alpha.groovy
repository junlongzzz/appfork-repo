import groovy.json.JsonSlurper
import plus.junlong.appfork.ScriptVars

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

static def checkUpdate(manifest, args) {
    def httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(60000))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    def response = httpClient.send(
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