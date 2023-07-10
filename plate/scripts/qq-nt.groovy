import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/windowsDownloadUrl.js'.toURL().text
    def matcher = response.replace(' ', '') =~ 'params=(\\{.*})'
    if (!matcher.find()) {
        return null
    }
    def object = new JsonSlurper().parseText(matcher.group(1))
    def url = object.ntDownloadX64Url
    def versionMatcher = url =~ '/QQ([\\d.]+)_x64.exe'
    if (!versionMatcher.find()) {
        return null
    }

    return [
            version: versionMatcher.group(1),
            url    : url
    ]
}
