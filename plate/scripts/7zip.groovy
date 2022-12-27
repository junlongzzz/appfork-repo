static def checkUpdate(version, platform, args) {
    def response = "https://www.7-zip.org/download.html".toURL().text
    def matcher = response =~ 'Download 7-Zip ([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    def versionNew = matcher[0][1] as String
    if (versionNew == null || versionNew.length() <= 0 || versionNew.equalsIgnoreCase(version as String)) {
        return null
    }
    version = versionNew.replaceAll("\\.", "")
    return [
            'version': versionNew,
            'url'    : [
                    'x64'  : "https://www.7-zip.org/a/7z${version}-x64.exe".toString(),
                    'x86'  : "https://www.7-zip.org/a/7z${version}.exe".toString(),
                    'arm64': "https://www.7-zip.org/a/7z${version}-arm64.exe".toString(),
            ]
    ]
}