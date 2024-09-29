import groovy.json.JsonSlurper

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
                    .header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
                            'Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0')
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