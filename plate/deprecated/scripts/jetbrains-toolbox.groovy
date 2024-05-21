import groovy.json.JsonSlurper

static def checkUpdate(manifest, args) {
    def response = 'https://data.services.jetbrains.com/products/releases?code=TBA&latest=true&type=release'.toURL().text
    def TBA0 = new JsonSlurper().parseText(response).TBA[0]

    def url = switch (manifest.platform) {
        case 'windows' -> TBA0.downloads.windows.link
        case 'linux' -> TBA0.downloads.linux.link
        case 'mac' -> [
                'macOS Intel'        : TBA0.downloads.mac.link,
                'macOS Apple Silicon': TBA0.downloads.macM1.link,
        ]
        default -> null
    }

    return [
            'version': TBA0.build,
            'url'    : url
    ]
}
