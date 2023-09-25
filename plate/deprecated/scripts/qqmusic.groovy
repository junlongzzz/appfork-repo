static def checkUpdate(version, platform, args) {
    def response = 'https://c.y.qq.com/pcupdate/fcgi-bin/fcg_update_player.fcg?cmd=QueryModuleUpdate&module_type=QQ&MusicNew&update_strategy=0'.toURL().text
    def versionMatcher = response =~ '<module_version>(.*)</module_version>'
    def urlMatcher = response =~ '<module_ws_url>(.*)</module_ws_url>'
    if (!versionMatcher.find() || !urlMatcher.find()) {
        return null
    }

    return [
            version: versionMatcher.group(1) as String,
            url    : urlMatcher.group(1) as String
    ]
}
