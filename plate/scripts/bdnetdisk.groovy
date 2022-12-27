import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = "https://pan.baidu.com/disk/cmsdata?clienttype=0&app_id=250528&t=${System.currentTimeMillis()}&do=client".toURL().text
    def jsonData = new JsonSlurper().parseText(response)
    if (jsonData.errorno != 0) {
        return null
    }
    def data = switch (platform) {
        case 'windows' -> jsonData.guanjia
        case 'linux' -> jsonData.linux
        case 'mac' -> jsonData.mac
        case 'android' -> jsonData.android
        default -> null
    }
    if (data == null) {
        return null
    }
    def versionNew = data.version
    def matcher = versionNew =~ '[vV]([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    def url = switch (platform) {
        case 'linux' -> [
                '.rpm': data.url,
                '.deb': data.url_1
        ]
        case 'mac' -> [
                'x64'  : data.url,
                'arm64': data.url_1
        ]
        default -> data.url
    }

    return [
            'version': version,
            'url'    : url
    ]
}
