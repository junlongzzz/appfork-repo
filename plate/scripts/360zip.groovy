import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://yasuo.360.cn/update/versioninfo.json'.toURL().text
    def jsonData = new JsonSlurper().parseText(response)
    if (jsonData.errno != 0) {
        return null
    }

    def versionNew = null
    def url = null

    switch (platform) {
        case 'windows':
            versionNew = jsonData.data.winCode
            url = jsonData.data.winDownloadUrl
            break
        case 'mac':
            versionNew = jsonData.data.macCode
            url = jsonData.data.macDownloadUrl
            break
        default:
            return null
    }

    return [
            'version': versionNew,
            'url'    : url
    ]
}