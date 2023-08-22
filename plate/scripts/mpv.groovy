static def checkUpdate(version, platform, args) {
    def response = 'https://sourceforge.net/projects/mpv-player-windows/files/release/'.toURL().text
    def matcher = response =~ 'mpv-([\\d.]+)-'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String
    return [
            'version': version,
            'url'    : [
                    '64Bit': "https://downloads.sourceforge.net/project/mpv-player-windows/release/mpv-${version}-x86_64.7z".toString(),
                    '32Bit': "https://downloads.sourceforge.net/project/mpv-player-windows/release/mpv-${version}-i686.7z".toString(),
            ]
    ]
}