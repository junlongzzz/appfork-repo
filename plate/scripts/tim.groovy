import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://im.qq.com/rainbow/TIMDownload/'.toURL().text
    def matcher = response =~ 'params= *(\\{.*})'
    if (!matcher.find()) {
        return null
    }
    def jsonData = new JsonSlurper().parseText(matcher[0][1] as String)
    def download = jsonData.app.download
    return switch (platform) {
        case 'windows' -> [
                'version': download.pcVersion,
                'url'    : download.pcLink
        ]
        case 'android' -> [
                'version': download.androidVersion,
                'url'    : download.androidLink
        ]
        default -> null
    }
}
