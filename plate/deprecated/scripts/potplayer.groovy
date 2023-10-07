static def checkUpdate(version, platform, args) {
    def response = 'https://t1.daumcdn.net/potplayer/PotPlayer/v4/Update2/UpdateEng.html'.toURL().text
    def matcher = response =~ '\\[(\\d+)]'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    return [
            'version': version,
            'url'    : [
                    '64Bit': "https://t1.daumcdn.net/potplayer/PotPlayer/Version/${version}/PotPlayerSetup64.exe".toString(),
                    '32Bit': "https://t1.daumcdn.net/potplayer/PotPlayer/Version/${version}/PotPlayerSetup.exe".toString(),
            ]
    ]
}