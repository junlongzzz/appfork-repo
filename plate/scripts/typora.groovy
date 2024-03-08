static def checkUpdate(manifest, args) {
    def platform = manifest.platform as String

    // 目前所有平台版本一致
//    def response = 'https://typoraio.cn/releases/stable.html'.toURL().text
    def response = 'https://typora.io/releases/stable'.toURL().text
    def pattern = switch (platform) {
        case 'windows' -> 'windows/typora-setup-x64-([\\d.]+).exe'
        case 'linux' -> 'linux/Typora-linux-x64-([\\d.]+).tar.gz'
        case 'mac' -> 'mac/Typora-([\\d.]+).dmg'
        default -> null
    }
    def matcher = response =~ pattern
    if (!matcher.find()) {
        return null
    }
    def version = matcher.group(1)

    return [
            'version': version,
            'url'    : switch (platform) {
                case 'windows' -> "https://download2.typoraio.cn/windows/typora-setup-x64-${version}.exe".toString()
                case 'linux' -> "https://download2.typoraio.cn/linux/Typora-linux-x64-${version}.tar.gz".toString()
                case 'mac' -> "https://download2.typoraio.cn/mac/Typora-${version}.dmg".toString()
                default -> null
            }
    ]
}
