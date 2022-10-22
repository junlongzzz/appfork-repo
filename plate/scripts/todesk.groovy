static def checkUpdate(version, platform, args) {
    def url = switch (platform) {
        case 'windows' -> 'https://update.todesk.com/windows/uplog.html'
        case 'mac' -> 'https://dl.todesk.com/macos/uplog.html'
        case 'linux' -> 'https://update.todesk.com/linux/uplog.html'
        case 'android' -> 'https://update.todesk.com/android/uplog.html'
        default -> null
    }

    if (url == null) {
        return null
    }

    def response = url.toURL().text
    def matcher = response =~ '>([\\d.]+)</div>'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    url = switch (platform) {
        case 'windows' -> [
                '全功能版': 'https://dl.todesk.com/windows/ToDesk_Setup.exe',
                '精简版'  : 'https://dl.todesk.com/windows/ToDesk_Lite_x64.exe'
        ]
        case 'mac' -> "https://dl.todesk.com/macos/ToDesk_${version}.pkg".toString()
        case 'linux' -> [
                'Debian/Ubuntu/Mint (x64)'  : "https://dl.todesk.com/linux/todesk_${version}_amd64.deb".toString(),
                'Arch Linux (x64)'          : "https://dl.todesk.com/linux/todesk_${version}_x86_64.pkg.tar.zst".toString(),
                'Fedora/CentOS/RedHat (x64)': "https://dl.todesk.com/linux/todesk_${version}_x86_64.rpm".toString(),
                'Raspberry Pi 4 Arm v7'     : "https://dl.todesk.com/linux/todesk_${version}_armv7l.deb".toString(),
                'Arm64 & aarch64 (rpm)'     : "https://dl.todesk.com/linux/todesk_${version}_aarch64.rpm".toString(),
                'Arm64 & aarch64 (deb)'     : "https://dl.todesk.com/linux/todesk_${version}_aarch64.deb".toString(),
        ]
        case 'android' -> "https://dl.todesk.com/android/ToDesk_${version}.apk".toString()
        default -> null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
