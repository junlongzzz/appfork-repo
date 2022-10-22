import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    switch (platform) {
        case 'windows':
            platform = 'windows'
            break
        case 'linux':
            platform = 'linux'
            break
        case 'mac':
            platform = 'macos'
            break
        default:
            return null
    }
    // releases_windows releases_linux releases_macos
    def response = "https://storage.googleapis.com/flutter_infra_release/releases/releases_${platform}.json".toURL().text
    def jsonData = new JsonSlurper().parseText(response)
    def stableHash = jsonData.current_release.stable
    for (release in jsonData.releases) {
        if (release.hash == stableHash) {
            return [
                    'version': release.version,
                    'url'    : "${jsonData.base_url}/${release.archive}".toString()
            ]
        }
    }
}
