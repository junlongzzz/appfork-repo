import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat

static def checkUpdate(manifest, args) {
    if (args == null) {
        return null
    }

    // up主的用户id
    def mid = args.up_uid
    if (mid == null) {
        return null
    }

    def ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0'
    def httpClient = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    // 请求一次主页面拿到cookie，不然访问后面的接口回报-401非法访问错误
    httpClient.send(HttpRequest.newBuilder()
            .uri("https://space.bilibili.com/${mid}".toURI())
            .header('user-agent', ua)
            .GET().build(),
            HttpResponse.BodyHandlers.discarding())
    def request = HttpRequest.newBuilder()
            .uri("https://api.bilibili.com/x/space/wbi/arc/search?mid=${mid}&ps=1&pn=1&tid=0&keyword=&order=pubdate&platform=web&order_avoided=true&wts=${(long) (System.currentTimeMillis() / 1000)}".toURI())
            .header('user-agent', ua)
            .header('origin', 'https://space.bilibili.com')
            .header('referer', "https://space.bilibili.com/${mid}/video")
            .GET().build()
    def response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    def object = new JsonSlurper().parseText(response.body())
    if (object.code != 0) {
        return [error: "${object.code}_${object.message}".toString()]
    }
    def latestVideo = object.data.list.vlist[0]
    if (latestVideo == null) {
        return null
    }
    def videoName = "${latestVideo.title} [${new SimpleDateFormat('yyyy-MM-dd').format(new Date(latestVideo.created * 1000L))}]"
    def version = latestVideo.bvid
    return [
            version: version,
            url    : [
                    (videoName as String): "https://www.bilibili.com/video/${version}".toString()
            ]
    ]
}