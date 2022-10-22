static def checkUpdate(version, platform, args) {
    // 目前所有平台版本一致
    def response = 'https://typora.com.cn/releases/stable'.toURL().text
    def pattern = null
    switch (platform) {
        case 'windows':
            pattern = 'windows/typora-setup-x64-([\\d.]+).exe'
            break
        case 'linux':
            pattern = 'linux/Typora-linux-x64-([\\d.]+).tar.gz'
            break
        case 'mac':
            pattern = 'mac/Typora-([\\d.]+).dmg'
            break
        default:
            return null
    }
    def matcher = response =~ pattern
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]

    def url = null
    switch (platform) {
        case 'windows':
            url = "https://download.typora.io/windows/typora-setup-x64-${version}.exe".toString()
            break
        case 'linux':
            url = "https://download.typora.io/linux/Typora-linux-x64-${version}.tar.gz".toString()
            break
        case 'mac':
            url = "https://download.typora.io/mac/Typora-${version}.dmg".toString()
            break
        default:
            return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
