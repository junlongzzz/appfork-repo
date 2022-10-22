import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    // https://pan.baidu.com/disk/cmsdata?platform=guanjia&page=1&num=1&time=1638699025247&channel=chunlei&clienttype=0&web=1
    switch (platform) {
        case 'windows':
            platform = 'guanjia'
            break
        case 'linux':
            platform = 'linux'
            break
        case 'mac':
            platform = 'mac'
            break
        case 'android':
            platform = 'android'
            break
        default:
            return null
    }
    def response = "https://pan.baidu.com/disk/cmsdata?platform=${platform}&page=1&num=1&time=${System.currentTimeMillis()}&channel=chunlei&clienttype=0&web=1".toURL().text
    def jsonData = new JsonSlurper().parseText(response)
    if (jsonData.errorno != 0) {
        return null
    }
    def data = jsonData.list[0]
    def versionNew = data.version
    def matcher = versionNew =~ "[vV]([\\d.]+)"
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]
    def url = null
    if (platform == 'linux') {
        url = [
                ".deb": data.url_1,
                ".rpm": data.url
        ]
    } else {
        url = data.url
    }

    return [
            'version': version,
            'url'    : url
    ]

}