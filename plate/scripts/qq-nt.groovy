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
    def response = checkUrl.toURL().text
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
        url = isNt ? object.ntDownloadX64Url : object.downloadUrl
        def versionMatcher = url =~ (isNt ? '/QQ_([\\d._]+)_x64_(\\d+).exe' : '/QQ_([\\d.]+)_(\\d+).exe')
        if (!versionMatcher.find()) {
            return null
        }
        version = versionMatcher.group(1)
        if (isNt) {
            version = version.replace('_', '.')
        }
    }

    return [
            version: version,
            url    : url
    ]
}
