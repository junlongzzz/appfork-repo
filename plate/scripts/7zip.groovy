static def checkUpdate(manifest, args) {
    def response = "https://www.7-zip.org".toURL().text
    def matcher = response =~ 'Download 7-Zip ([\\d.]+) \\(\\d{4}-\\d{2}-\\d{2}\\)'
    if (!matcher.find()) {
        return null
    }
    def version = matcher.group(1)
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