static def checkUpdate(manifest, args) {
    def response = 'https://www.diskgenius.cn/download.php'.toURL().text
    def matcher = response =~ '\\[[vV]([\\d.]+)]'
    if (!matcher.find()) {
        return null
    }
    def version = matcher.group(1)
    return [
            'version': version,
            'url'    : "https://download.eassos.cn/DG${version.replaceAll('\\.', '')}_x64.zip".toString()
    ]
}
