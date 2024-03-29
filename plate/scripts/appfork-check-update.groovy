import com.jayway.jsonpath.JsonPath
import groovy.json.JsonSlurper
import org.dom4j.DocumentHelper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

static def checkUpdate(manifest, args) {
    if (args == null) {
        return null
    }

    def version = manifest.version as String
    def platform = manifest.platform as String

    // 检测更新url不存在就默认使用主页地址
    def checkUrl = args.url ? args.url as String : manifest.homepage as String
    def regex = args.regex as String
    def jsonpath = args.jsonpath as String
    def xpath = args.xpath as String
    def updateUrl = args.autoupdate ? args.autoupdate : args[platform]

    def githubParams = args.gh
    def githubPreRelease = false
    // github api 返回的json结果内查找assets下载链接的jsonpath
    def githubAssetsJsonpath = null

    if (!checkUrl) {
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

        def response = new JsonSlurper().parseText(httpClient.send(HttpRequest.newBuilder()
                .uri("https://luban.m.qq.com/api/public/software-manager/softwareProxy".toURI())
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .header('Accept-Language', 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6')
                .header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
                        'Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0')
                .POST(HttpRequest.BodyPublishers.ofString("cmdid=3318&jprxReq[req][soft_id_list][]=${appId}")).build(),
                HttpResponse.BodyHandlers.ofString()).body())
        if (response.resp == null || response.resp.retCode != 0 ||
                response.resp.soft_list == null || response.resp.soft_list.isEmpty()) {
            return null
        }
        def softInfo = response.resp.soft_list[0]
        // 舍弃使用返回的下载链接，因为每次请求的下载链接都不一样，会造成频繁的更新清单文件
        // 故直接使用软件中心的详情页链接用户前往下载
//        def downloadUrl = softInfo.download_https_url
//        if (downloadUrl == null) {
//            downloadUrl = softInfo.download_url
//        }
        return [
                version: softInfo.ver_name as String,
                url    : [
                        '前往下载': "https://pc.qq.com/detail/${categoryId}/detail_${appId}.html" as String
                ]
        ]
    }

    // 检查是不是github检测更新方式，格式为：gh://<用户名>/<仓库名>
    urlMatcher = checkUrl =~ 'gh://(.+)/(.+)'
    if (urlMatcher.find()) {
        def owner = urlMatcher.group(1)
        def repo = urlMatcher.group(2)

        if (githubParams instanceof Map && !githubParams.isEmpty()) {
            if (githubParams.assets_jsonpath instanceof String && githubParams.assets_jsonpath) { // 是否检测下载文件jsonpath
                checkUrl = "https://api.github.com/repos/${owner}/${repo}/releases/latest" as String
                jsonpath = '$.tag_name' as String
                // 保存查找下载链接的jsonpath
                githubAssetsJsonpath = githubParams.assets_jsonpath as String
            }
            if (githubParams.prerelease instanceof Boolean && githubParams.prerelease) { // 是否检测预发布版本
                checkUrl = "https://api.github.com/repos/${owner}/${repo}/releases" as String
                githubPreRelease = true
            }
        }
        // 如果没有额外参数，使用默认方式检测最新版本
        if (checkUrl.startsWith('gh://')) {
            // 将检测更新链接转换为github最新release链接
            checkUrl = "https://github.com/${owner}/${repo}/releases/latest" as String
            if (!regex) {
                regex = '/releases/tag/[vV]?([\\d.-]+)'
            }
        }
    }

    def response = httpClient.send(HttpRequest.newBuilder().uri(checkUrl.toURI()).GET().build(), HttpResponse.BodyHandlers.ofString()).body()
    // 开始用对应方式查找版本号
    if (githubPreRelease) { // github预发布版本
        def result = JsonPath.read(response, '$.*[?(@.prerelease == true)]')
        if (result instanceof List && !result.isEmpty()) {
            result = result[0]
        } else {
            return null
        }
        version = result.tag_name as String
        response = result
    } else if (regex) { // 正则
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
    }

    if (version) {
        // 去除版本号前面的v
        if (version.startsWith('v') || version.startsWith('V')) {
            version = version.substring(1)
        }
    } else {
        return null
    }

    def url = null
    if (githubAssetsJsonpath) {
        // 使用jsonpath方式读取查找下载链接
        url = [:]
        JsonPath.read(response, githubAssetsJsonpath).each { asset ->
            def label = asset.label as String
            def name = asset.name as String
            url[label ? label : name] = asset.browser_download_url
        }
    } else {
        def versionReplaceRegex = '\\$\\{?version}?'
        if (updateUrl instanceof String) {
            url = updateUrl.replaceAll(versionReplaceRegex, version)
        } else if (updateUrl instanceof Map) {
            url = [:]
            updateUrl.forEach((String k, String v) -> {
                url[k.replaceAll(versionReplaceRegex, version)] = v.replaceAll(versionReplaceRegex, version)
            })
        }
    }

    return [
            version: version,
            url    : url
    ]
}
