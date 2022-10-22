static def checkUpdate(version, platform, args) {
    def response = 'https://geekuninstaller.com/download'.toURL().text
    def matcher = response =~ '>([\\d.]+)</b>'
    if (!matcher.find()) {
        return null
    }
    return [
            'version': matcher[0][1],
            'url'    : 'https://geekuninstaller.com/geek.zip'
    ]
}
