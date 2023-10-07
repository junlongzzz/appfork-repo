static def checkUpdate(version, platform, args) {
    def response = "https://www.7-zip.org/download.html".toURL().text
    def matcher = response =~ 'Download 7-Zip ([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher.group(1)
    def versionNew = version.replaceAll("\\.", "")
    return [
            'version': version,
            'url'    : [
                    'x64'  : "https://www.7-zip.org/a/7z${versionNew}-x64.exe".toString(),
                    'x86'  : "https://www.7-zip.org/a/7z${versionNew}.exe".toString(),
                    'arm64': "https://www.7-zip.org/a/7z${versionNew}-arm64.exe".toString(),
            ]
    ]
}