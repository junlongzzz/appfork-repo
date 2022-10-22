static def checkUpdate(version, platform, args) {
    def response = "https://www.bandisoft.com/bandizip/history/".toURL().text
    def matcher = response =~ "v([\\d.]+)</font>"
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]
    return [
            'version': version,
            'url'    : 'https://www.bandisoft.com/bandizip/dl.php?web'
    ]
}