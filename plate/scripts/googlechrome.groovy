import groovy.xml.XmlSlurper

static def checkUpdate(manifest, args) {
    def response = 'https://scoopinstaller.github.io/UpdateTracker/googlechrome/chrome.min.xml'.toURL().text
    def chromechecker = new XmlSlurper().parseText(response)
    def version = chromechecker.stable64.version.text()
    def url = null
    chromechecker.stable64.download.url.each {
        url = it.text()
        if (url.contains('dl.google.com')) {
            return [
                    'version': version,
                    'url'    : url
            ]
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
