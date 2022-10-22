import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/UnblockNeteaseMusic/server/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = null
    switch (platform) {
        case 'windows':
            url = [
                    'amd64': "https://github.com/UnblockNeteaseMusic/server/releases/download/v${version}/unblockneteasemusic-win-x64.exe".toString(),
                    'arm64': "https://github.com/UnblockNeteaseMusic/server/releases/download/v${version}/unblockneteasemusic-win-arm64.exe".toString(),
            ]
            break
        case 'linux':
            url = [
                    'amd64': "https://github.com/UnblockNeteaseMusic/server/releases/download/v${version}/unblockneteasemusic-linux-x64".toString(),
                    'arm64': "https://github.com/UnblockNeteaseMusic/server/releases/download/v${version}/unblockneteasemusic-linux-arm64".toString(),
            ]
            break
        default:
            return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
