static def checkUpdate(version, platform, args) {
    if (!args) {
        return null
    }
    def baseUrl = 'https://dlcdn.apache.org'

    def response = "${baseUrl}/tomcat/tomcat-${args.ver}/?C=M;O=D".toURL().text
    def matcher = response =~ 'v([\\d.]+)/'
    if (!matcher.find()) {
        return null
    }
    version = matcher.group(1)

    def url = [:]
    ["${baseUrl}/tomcat/tomcat-${args.ver}/v${version}/bin/apache-tomcat-${version}.exe" as String,
     "${baseUrl}/tomcat/tomcat-${args.ver}/v${version}/bin/apache-tomcat-${version}-windows-x64.zip" as String,
     "${baseUrl}/tomcat/tomcat-${args.ver}/v${version}/bin/apache-tomcat-${version}-windows-x86.zip" as String].each {
        url[it.substring(it.lastIndexOf('/') + 1)] = it
    }

    return [
            version: version,
            url    : url
    ]
}