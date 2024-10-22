import plus.junlong.appfork.ScriptVars

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

static def checkUpdate(manifest, args) {
    //    def html = "https://pc.qq.com/detail/7/detail_11167.html".toURL().text
//    def matcher = html =~ "/cloudmusicsetup([\\d.]+).exe"
//    if (!matcher.find()) {
//        return null
//    }
//    return matcher[0][1]
//    def response = "https://raw.fastgit.org/chawyehsu/dorado/master/bucket/neteasemusic.json".toURL().text
//    def response = "https://cdn.jsdelivr.net/gh/chawyehsu/dorado@master/bucket/neteasemusic.json".toURL().text
//    def response = "https://gitee.com/chawyehsu/dorado/raw/master/bucket/neteasemusic.json".toURL().text
//    def response = "https://ghproxy.com/https://raw.githubusercontent.com/chawyehsu/dorado/master/bucket/neteasemusic.json".toURL().text
//    return new JsonSlurper().parseText(response).version

//    def headers = OkHttpUtil.head('https://music.163.com/api/pc/package/download/latest')
//    if (headers == null) {
//        return null
//    }

//    def connection = (HttpURLConnection) 'https://music.163.com/api/pc/package/download/latest'.toURL().openConnection()
//    connection.setRequestMethod('HEAD')
//    connection.setConnectTimeout(10000)
//    connection.setInstanceFollowRedirects(false)
//    def location = connection.getHeaderField('Location')

    def url = 'https://music.163.com/api/pc/package/download/latest'
    def httpClient = ScriptVars.newHttpClientBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    def request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header('User-Agent', ScriptVars.USER_AGENT)
            .method('HEAD', HttpRequest.BodyPublishers.noBody())
            .build()
    def response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    def location = response.headers().firstValue('location').orElse(null)
    def matcher = location =~ '/*_([\\d.]+)'
    if (!matcher.find()) {
        return null
    }

    return [
            version: matcher.group(1),
            url    : url
    ]
}

