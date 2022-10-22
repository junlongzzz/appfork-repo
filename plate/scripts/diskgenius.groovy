static def checkUpdate(version, platform, args) {
    def response = 'https://www.diskgenius.cn/download.php'.toURL().text
    def matcher = response =~ '\\[[vV]([\\d.]+)]'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]
    return [
            'version': version,
            'url'    : "https://download.eassos.cn/DG${version.replaceAll('\\.', '')}_x64.zip".toString()
    ]
}
