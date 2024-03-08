import groovy.json.JsonSlurper

static def checkUpdate(manifest, args) {
    def platform = manifest.platform as String

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
        url = args.nt ? object.ntDownloadX64Url : object.downloadUrl
        def versionMatcher = url =~ (args.nt ? '/QQ([\\d.]+)_x64.exe' : '/QQ([\\d.]+).exe')
        if (!versionMatcher.find()) {
            return null
        }
        version = versionMatcher.group(1)
    }

    return [
            version: version,
            url    : url
    ]
}
