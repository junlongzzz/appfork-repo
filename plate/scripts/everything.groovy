static def checkUpdate(version, platform, args) {
    def response = 'https://www.voidtools.com'.toURL().text
    def matcher = response =~ 'Download Everything ([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]

    return [
            'version': version,
            'url'    : "https://www.voidtools.com/Everything-${version}.x64-Setup.exe".toString()
    ]
}
