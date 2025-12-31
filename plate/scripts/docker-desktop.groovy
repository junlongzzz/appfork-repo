import com.alibaba.fastjson2.JSONObject
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String

        if (!(platform in ['windows', 'mac', 'linux'])) {
            return null
        }

        def response = 'https://docs.docker.com/desktop/release-notes/'.toURL().text
        def matcher = response =~ '>([\\d.]+)</a>'
        if (!matcher.find()) {
            return null
        }
        def version = matcher.group(1)

        def url = switch (platform) {
            case 'windows' -> [
                    'Amd64': 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe',
                    'Arm64': 'https://desktop.docker.com/win/main/arm64/Docker%20Desktop%20Installer.exe'
            ]
            case 'mac' -> [
                    'Mac with Intel chip': 'https://desktop.docker.com/mac/main/amd64/Docker.dmg',
                    'Mac with Apple chip': 'https://desktop.docker.com/mac/main/arm64/Docker.dmg'
            ]
            case 'linux' -> [
                    'Linux DEB' : "https://desktop.docker.com/linux/main/amd64/docker-desktop-amd64.deb".toString(),
                    'Linux RPM' : "https://desktop.docker.com/linux/main/amd64/docker-desktop-x86_64.rpm".toString(),
                    'Linux Arch': "https://desktop.docker.com/linux/main/amd64/docker-desktop-x86_64.pkg.tar.zst".toString()
            ]
            default -> null
        }

        return [
                version: version,
                url    : url
        ]
    }

}