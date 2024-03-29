import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/halo-dev/halo/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    return [
            'version': version,
            'url'    : [
                    'via Docker': 'https://hub.docker.com/r/halohub/halo'
            ]
    ]
}
