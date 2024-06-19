import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

static def checkUpdate(manifest, args) {
//    def response = "https://pan.baidu.com/disk/cmsdata?clienttype=0&app_id=250528&t=${System.currentTimeMillis()}&do=client".toURL().text

    def platform = manifest.platform as String

    def ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.60'

    def httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(60000))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    def response = httpClient.send(
            HttpRequest.newBuilder("https://pan.baidu.com/disk/cmsdata?clienttype=0&app_id=250528&t=${System.currentTimeMillis()}&do=client".toURI())
                    .header("user-agent", ua)
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
