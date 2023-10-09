import com.jayway.jsonpath.JsonPath
import org.dom4j.DocumentHelper
import org.jsoup.Jsoup

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

static def checkUpdate(version, platform, args) {
    if (args == null) {
        return null
    }

    def checkUrl = args.url as String
    def regex = args.regex as String
    def jsonpath = args.jsonpath as String
    def xpath = args.xpath as String
    def updateUrl = args.autoupdate ? args.autoupdate : args[platform as String]

    if (!checkUrl || !updateUrl) {
        return null
    }

    def httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofMillis(30000))
            .build()

    // 判断是不是腾讯软件中心的检测方式链接，格式为 tsc://<分类ID>/<应用ID>
    // tsc为Tencent Software Center的缩写
    def urlMatcher = checkUrl =~ 'tsc://(\\d+)/(\\d+)'
    if (urlMatcher.find()) {
        // 开始通过腾讯软件中心的方式查找版本号和下载链接
        def categoryId = urlMatcher.group(1)
        def appId = urlMatcher.group(2)

        def document = Jsoup.parse(httpClient.send(HttpRequest.newBuilder()
                .uri("https://pc.qq.com/detail/${categoryId}/detail_${appId}.html".toURI())
                .header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.41')
                .GET().build(), HttpResponse.BodyHandlers.ofString()).body())
        if (!regex) {
            // 默认的版本查找匹配正则表达式
            regex = '>版本：([\\d.]+)<'
        }
        def versionMatcher = document.html() =~ regex
        if (!versionMatcher.find()) {
            return null
        }
        return [
                version: versionMatcher.group(1),
                url    : document.selectFirst("a[data-id='${appId}']").attr('href')
        ]
    }

    // 检查是不是github检测更新方式，格式为：gh://<用户名>/<仓库名>
    urlMatcher = checkUrl =~ 'gh://(.+)/(.+)'
    if (urlMatcher.find()) {
        def owner = urlMatcher.group(1)
        def repo = urlMatcher.group(2)
        // 将检测更新链接转换为github最新release链接
        checkUrl = "https://github.com/${owner}/${repo}/releases/latest" as String
        if (!regex) {
            regex = '/releases/tag/[vV]?([\\d.]+)'
        }
    }

    def response = httpClient.send(HttpRequest.newBuilder()
            .uri(checkUrl.toURI())
            .GET().build(), HttpResponse.BodyHandlers.ofString()).body()
    // 开始用对应方式查找版本号
    if (regex) { // 正则
        def matcher = response =~ regex
        if (!matcher.find()) {
            return null
        }
        version = matcher.group(1)
    } else if (jsonpath) { // jsonpath
        def read = JsonPath.read(response, jsonpath)
        if (read instanceof List) {
            version = read[0] as String
        } else {
            version = read as String
        }
    } else if (xpath) { // xml
        def document = DocumentHelper.parseText(response)
        def node = document.selectSingleNode(xpath)
        if (!node) {
            return null
        }
        version = node.getText()
    } else {
        return null
    }

    def url = null
    def versionReplaceRegex = '\\$\\{?version}?'
    if (updateUrl instanceof String) {
        url = updateUrl.replaceAll(versionReplaceRegex, version)
    } else if (updateUrl instanceof Map) {
        url = [:]
        updateUrl.forEach((String k, String v) -> {
            url[k.replaceAll(versionReplaceRegex, version)] = v.replaceAll(versionReplaceRegex, version)
        })
    }

    return [
            version: version,
            url    : url
    ]
}
