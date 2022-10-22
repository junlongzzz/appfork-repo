import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = "https://api.github.com/repos/qishibo/AnotherRedisDesktopManager/releases/latest".toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')
    def url = null
    switch (platform) {
        case 'windows':
            url = "https://github.com/qishibo/AnotherRedisDesktopManager/releases/download/v${version}/Another-Redis-Desktop-Manager.${version}.exe".toString()
            break
        case 'linux':
            url = "https://github.com/qishibo/AnotherRedisDesktopManager/releases/download/v${version}/Another-Redis-Desktop-Manager.${version}.AppImage".toString()
            break
        case 'mac':
            url = [
                    'x86'  : "https://github.com/qishibo/AnotherRedisDesktopManager/releases/download/v${version}/Another-Redis-Desktop-Manager.${version}.dmg".toString(),
                    'arm64': "https://github.com/qishibo/AnotherRedisDesktopManager/releases/download/v${version}/Another-Redis-Desktop-Manager-M1-arm64-${version}.dmg".toString()
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
