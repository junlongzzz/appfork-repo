import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

static def checkUpdate(manifest, args) {
    def httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(30000))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    def response = httpClient.send(
            HttpRequest.newBuilder('https://yasuo.360.cn/update/versioninfo.json'.toURI())
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