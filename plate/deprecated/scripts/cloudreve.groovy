import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/cloudreve/Cloudreve/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = null
    switch (platform) {
        case 'windows':
            url = "https://github.com/cloudreve/Cloudreve/releases/download/${version}/cloudreve_${version}_windows_amd64.zip".toString()
            break
        case 'linux':
            url = [
                    'amd64': "https://github.com/cloudreve/Cloudreve/releases/download/${version}/cloudreve_${version}_linux_amd64.tar.gz".toString(),
                    'arm64': "https://github.com/cloudreve/Cloudreve/releases/download/${version}/cloudreve_${version}_linux_arm64.tar.gz".toString(),
                    'arm'  : "https://github.com/cloudreve/Cloudreve/releases/download/${version}/cloudreve_${version}_linux_arm.tar.gz".toString()
            ]
            break
        default:
            return null
    }
    return [
            'version': version,
            'url'    : url
    ]
}