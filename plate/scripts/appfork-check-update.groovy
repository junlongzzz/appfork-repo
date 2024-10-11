import com.jayway.jsonpath.JsonPath
import groovy.json.JsonSlurper
import org.dom4j.DocumentHelper
import plus.junlong.appfork.ScriptVars

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

    def githubParams = args.gh == null ? args.github : args.gh
    def githubPreRelease = false
    // github api 返回的json结果内查找assets下载链接的jsonpath
    def githubAssetsJsonpath = null

    if (!checkUrl) {
        return null
    }

    def httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofMillis(60000))
            .build()
    // 默认的user-agent
    def userAgent = ScriptVars.USER_AGENT

    // 判断是不是腾讯软件中心的检测方式链接，格式为 tsc://<分类ID>/<应用ID>
    // tsc为Tencent Software Center的缩写
    def urlMatcher = checkUrl =~ '(?i)^tsc://(?<categoryId>\\d+)/(?<appId>\\d+)$'
    if (urlMatcher.find()) {
        // 开始通过腾讯软件中心的方式查找版本号和下载链接
        def categoryId = urlMatcher.group('categoryId')
        def appId = urlMatcher.group('appId')

        def response = new JsonSlurper().parseText(httpClient.send(HttpRequest.newBuilder()
                .uri("https://luban.m.qq.com/api/public/software-manager/softwareProxy".toURI())
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .header('Accept-Language', 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6')
                .header('User-Agent', userAgent)
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

    // 判断是否是github或gitee平台的项目地址，转换为特定平台协议开头的链接地址，如 github://<owner>/<repo>
    urlMatcher = checkUrl =~ '(?i)^https://(?<protocol>github|gitee).com/(?<owner>[\\w-.]+)/(?<repo>[\\w-.]+)$'
    if (urlMatcher.find()) {
        checkUrl = "${urlMatcher.group('protocol')}://${urlMatcher.group('owner')}/${urlMatcher.group('repo')}"
    }

    // 检查是不是github|gitee检测更新方式，格式为：<平台名称>://<用户名>/<仓库名>
    urlMatcher = checkUrl =~ '(?i)^(?<protocol>gh|github|gitee)://(?<owner>[\\w-.]+)/(?<repo>[\\w-.]+)$'
    if (urlMatcher.find()) {
        def protocol = urlMatcher.group('protocol')
        def owner = urlMatcher.group('owner')
        def repo = urlMatcher.group('repo')
        // 默认的git平台版本检测正则表达式：数字、字母、下划线、点、横线
        def verRegex = '/releases/tag/(?<version>[\\w.-]+)'

        if (protocol == 'gh' || protocol == 'github') {
            // github平台
            if (githubParams instanceof Map && !githubParams.isEmpty()) {
                if (githubParams.assets_jsonpath instanceof String && githubParams.assets_jsonpath) {
                    // 是否检测下载文件jsonpath
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
            if (checkUrl.startsWith('gh://') || checkUrl.startsWith('github://')) {
                // 将检测更新链接转换为github最新release链接
                checkUrl = "https://github.com/${owner}/${repo}/releases/latest" as String
                if (!regex) {
                    regex = verRegex
                }
            }
        } else if (protocol == 'gitee') {
            // gitee平台
            checkUrl = "https://gitee.com/${owner}/${repo}/releases/latest" as String
            if (!regex) {
                regex = verRegex
            }
        }
    }

    def response = httpClient.send(
            HttpRequest.newBuilder().uri(checkUrl.toURI()).header('User-Agent', userAgent).GET().build(),
            HttpResponse.BodyHandlers.ofString()).body()
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
        if (regex.contains('?<version>')) {
            // 通过命名组获取版本号
            version = matcher.group('version')
        } else {
            // 通过索引获取版本号
            version = matcher.group(1)
        }
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
        if (updateUrl instanceof String) {
            url = handleVersionReplace(updateUrl, version)
        } else if (updateUrl instanceof Map) {
            url = [:]
            updateUrl.forEach((String key, String value) -> {
                url[handleVersionReplace(key, version)] = handleVersionReplace(value, version)
            })
        } else if (updateUrl instanceof List) {
            url = []
            updateUrl.forEach((String item) -> {
                url << handleVersionReplace(item, version)
            })
        }
    }

    return [
            version: version,
            url    : url
    ]
}

/**
 * 处理版本号替换
 * @param str 需要进行替换处理的字符串
 * @param version 获取到的版本号
 * @return 处理后的字符串
 */
static String handleVersionReplace(String str, String version) {
    if (str == null || str.isEmpty() || version == null || version.isEmpty()) {
        return str
    }
    def matcher = str =~ '\\$\\{?(?<versionType>[a-zA-Z]+)}?'
    // 匹配语义化版本号 major.minor.patch-prerelease+buildmetadata
    def semVerMatcher = version =~ '^(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)' +
            '(?:-(?<prerelease>(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?' +
            '(?:\\+(?<buildmetadata>[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$'
    def isSemVer = semVerMatcher.find()
    while (matcher.find()) {
        def versionType = matcher.group('versionType').toLowerCase()
        def versionReplace = switch (versionType) {
            case 'version' -> version
            case 'cleanversion', 'clean' -> version.replaceAll('\\D', '') // 只有纯数字的版本号
            case 'majorversion', 'major' -> isSemVer ? semVerMatcher.group('major') : null
            case 'minorversion', 'minor' -> isSemVer ? semVerMatcher.group('minor') : null
            case 'patchversion', 'patch' -> isSemVer ? semVerMatcher.group('patch') : null
            case 'prereleaseversion', 'prerelease' -> isSemVer ? semVerMatcher.group('prerelease') : null
            case 'buildmetadataversion', 'buildmetadata' -> isSemVer ? semVerMatcher.group('buildmetadata') : null
            default -> null
        }
        if (versionReplace != null) {
            str = str.replace(matcher.group(), versionReplace)
        }
    }
    return str
}