import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://data.services.jetbrains.com/products/releases?code=TBA&latest=true&type=release'.toURL().text
    def TBA0 = new JsonSlurper().parseText(response).TBA[0]

    def url = null
    switch (platform) {
        case 'windows':
            url = TBA0.downloads.windows.link
            break
        case 'linux':
            url = TBA0.downloads.linux.link
            break
        case 'mac':
            url = [
                    'macOS Intel'        : TBA0.downloads.mac.link,
                    'macOS Apple Silicon': TBA0.downloads.macM1.link,
            ]
            break
        default:
            return null
    }

    return [
            'version': TBA0.build,
            'url'    : url
    ]
}
