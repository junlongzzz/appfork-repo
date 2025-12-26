import plus.junlong.appfork.ScriptVars

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

static def checkUpdate(manifest, args) {
    def (url, regex) = switch (manifest.platform) {
        case 'windows' -> ['https://www.foxmail.com/win/download', 'FoxmailSetup_([\\d.]+).exe']
        case 'mac' -> ['https://www.foxmail.com/mac/download', 'Foxmail_for_Mac_([\\d.]+).dmg']
        default -> [null, null]
    }

    if (url == null || regex == null) {
        return null
    }

//    def connection = (HttpURLConnection) url.toURL().openConnection()
//    connection.setRequestMethod('HEAD')
//    connection.setConnectTimeout(10000)
//    connection.setInstanceFollowRedirects(false)
//    def location = connection.getHeaderField('Location')

    def httpClient = ScriptVars.newHttpClientBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    def request = HttpRequest.newBuilder(URI.create(url))
            .header('User-Agent', ScriptVars.USER_AGENT)
            .method('HEAD', HttpRequest.BodyPublishers.noBody())
            .build()
    def response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    def location = response.headers().firstValue('location').orElse(null)

    def matcher = location =~ regex
    if (!matcher.find()) {
        return null
    }

    return [
            version: matcher.group(1),
            url    : url
    ]
}