import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://qzonestyle.gtimg.cn/qzone/qzactStatics/configSystem/data/1605/config1.js'.toURL().text
    def matcher = response =~ 'params= *(\\{.*})'
    if (!matcher.find()) {
        return null
    }
    def jsonData = new JsonSlurper().parseText(matcher[0][1].toString())
    def downloads = jsonData.app.download
    def url = null
    switch (platform) {
        case 'windows':
            version = downloads.pcVersion
            url = downloads.pcLink
            break
        case 'android':
            version = downloads.androidVersion
            url = downloads.androidLink
            break
        default:
            return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
