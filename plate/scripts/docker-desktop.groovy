static def checkUpdate(version, platform, args) {
    if (!(platform in ['windows', 'mac', 'linux'])) {
        return null
    }

    def response = 'https://docs.docker.com/desktop/release-notes/'.toURL().text
    def matcher = response =~ '/docker-desktop-([\\d.]+)-'
    if (!matcher.find()) {
        return null
    }
    version = matcher.group(1)

    def url = switch (platform) {
        case 'windows' -> 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe'
        case 'mac' -> [
                'Mac with Intel chip': 'https://desktop.docker.com/mac/main/amd64/Docker.dmg',
                'Mac with Apple chip': 'https://desktop.docker.com/mac/main/arm64/Docker.dmg'
        ]
        case 'linux' -> [
                'Linux DEB' : "https://desktop.docker.com/linux/main/amd64/docker-desktop-${version}-amd64.deb".toString(),
                'Linux RPM' : "https://desktop.docker.com/linux/main/amd64/docker-desktop-${version}-x86_64.rpm".toString(),
                'Linux Arch': "https://desktop.docker.com/linux/main/amd64/docker-desktop-${version}-x86_64.pkg.tar.zst".toString()
        ]
        default -> null
    }

    return [
            version: version,
            url    : url
    ]
}
