static def checkUpdate(version, platform, args) {
//    def response = "https://api.github.com/repos/gradle/gradle/releases/latest".toURL().text
//    def tagName = new JsonSlurper().parseText(response).tag_name
//    return tagName.replaceFirst("[vV]", "")
    def response = 'https://gradle.org/install/'.toURL().text
    def matcher = response =~ 'The current Gradle release is version ([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]

    return [
            'version': version,
            'url'    : [
                    'Binary-only' : "https://services.gradle.org/distributions/gradle-${version}-bin.zip".toString(),
                    'Complete-all': "https://services.gradle.org/distributions/gradle-${version}-all.zip".toString()
            ]
    ]
}
