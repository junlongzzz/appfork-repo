import cn.hutool.http.HttpUtil
import groovy.json.JsonSlurper
import plus.junlong.appfork.ScriptVars

static def checkUpdate(manifest, args) {
    def platform = manifest.platform as String
    def isNt = args ? (args.nt as Boolean) : false

    def timeout = 60000
    def headers = [
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
            'User-Agent'     : ScriptVars.USER_AGENT
    ]

    // 先获取网页，从网页内获取版本信息请求地址
    def html = HttpUtil.createGet(manifest.homepage as String, true).setProxy(Proxy.NO_PROXY)
            .timeout(timeout).headerMap(headers, true).execute().body()
    def urlMatcher = ((html == null || html.isEmpty()) ? '' : html).replace(' ', '') =~ 'varrainbowConfigUrl="(?<url>.*)";'
    def checkUrl
    if (urlMatcher.find()) {
        checkUrl = urlMatcher.group('url')
        if (!checkUrl.startsWithAny('https://', 'http://')) {
            if (checkUrl.startsWith('//')) {
                checkUrl = 'https:' + checkUrl
            } else {
                checkUrl = 'https://' + checkUrl
            }
        }
    } else {
        // 网页内未找到，使用默认地址
        checkUrl = switch (platform) {
            case 'windows' -> 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/windowsDownloadUrl.js'
            case 'linux' -> 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/linuxQQDownload.js'
            default -> null
        }
    }
    if (checkUrl == null) {
        return null
    }

    def response = HttpUtil.createGet(checkUrl, true).setProxy(Proxy.NO_PROXY)
            .timeout(timeout).headerMap(headers, true).execute().body()
    if (response == null || response.isEmpty()) {
        return null
    }
    def matcher = response.replace(' ', '') =~ 'params=(\\{.*})'
    if (!matcher.find()) {
        return null
    }
    def object = new JsonSlurper().parseText(matcher.group(1))

    def version = null
    def url = null
    if (platform == 'linux') {
        version = object.version
        url = object.x64DownloadUrl
    } else if (platform == 'windows') {
        version = isNt ? object.ntVersion : object.version
        url = isNt ? object.ntDownloadX64Url : object.downloadUrl
    }

    return [
            version: version,
            url    : url
    ]
}
