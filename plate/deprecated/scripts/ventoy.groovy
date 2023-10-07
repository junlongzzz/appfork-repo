import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/ventoy/Ventoy/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = null
    switch (platform) {
        case 'windows':
            url = "https://github.com/ventoy/Ventoy/releases/download/v${version}/ventoy-${version}-windows.zip".toString()
            break
        case 'linux':
            url = "https://github.com/ventoy/Ventoy/releases/download/v${version}/ventoy-${version}-linux.tar.gz".toString()
            break
        default:
            return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
