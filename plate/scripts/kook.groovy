import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    //    def response = "https://www.kaiheila.cn/api/v2/updates/latest-version?platform=${platform}".toURL().text
    def response = "https://www.kookapp.cn/api/v2/updates/latest-version?platform=${platform}".toURL().text
    def object = new JsonSlurper().parseText(response)
    return ['version': object.number, 'url': object.url]
}

