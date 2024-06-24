import cn.hutool.http.HttpUtil
import groovy.json.JsonSlurper

static def checkUpdate(manifest, args) {
    def platform = manifest.platform as String
    def isNt = args ? (args.nt as Boolean) : false

    def checkUrl = switch (platform) {
        case 'windows' -> 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/windowsDownloadUrl.js'
        case 'linux' -> 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/linuxQQDownload.js'
        default -> null
    }
    if (checkUrl == null) {
        return null
    }
    def response = HttpUtil.createGet(checkUrl, true)
            .timeout(60000)
            .header('Accept-Language', 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6')
            .header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
                    'Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0')
            .execute().body()
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
