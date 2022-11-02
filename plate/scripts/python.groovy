static def checkUpdate(version, platform, args) {
    def response = 'https://www.python.org/downloads/windows/'.toURL().text
    def matcher = response =~ 'Latest Python \\d+ Release - Python ([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    return [
            version: version,
            url    : [
                    'Installer (32-bit)': "https://www.python.org/ftp/python/${version}/python-${version}.exe" as String,
                    'Installer (64-bit)': "https://www.python.org/ftp/python/${version}/python-${version}-amd64.exe" as String,
                    'Installer (ARM64)' : "https://www.python.org/ftp/python/${version}/python-${version}-arm64.exe" as String
            ]
    ]
}