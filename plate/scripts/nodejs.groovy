static def checkUpdate(version, platform, args) {
    def checkUrl = 'https://nodejs.org/en/download/current/';
    def regex = 'Latest Current Version: <strong>([\\d.]+)</strong>'

    if (args && 'LTS'.equalsIgnoreCase(args.type as String)) {
        checkUrl = 'https://nodejs.org/en/download/'
        regex = 'Latest LTS Version: <strong>([\\d.]+)</strong>'
    }

    def matcher = checkUrl.toURL().text =~ regex
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    return [
            version: version,
            url    : [
                    'Installer (.msi 32-bit)': "https://nodejs.org/dist/v${version}/node-v${version}-x86.msi" as String,
                    'Installer (.msi 64-bit)': "https://nodejs.org/dist/v${version}/node-v${version}-x64.msi" as String,
                    'Binary (.zip 32-bit)'   : "https://nodejs.org/dist/v${version}/node-v${version}-win-x86.zip" as String,
                    'Binary (.zip 64-bit)'   : "https://nodejs.org/dist/v${version}/node-v${version}-win-x64.zip" as String,
                    'Official Docker Image'  : 'https://hub.docker.com/_/node/'
            ]
    ]
}