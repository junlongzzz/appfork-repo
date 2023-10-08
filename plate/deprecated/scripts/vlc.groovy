import org.jsoup.Jsoup

static def checkUpdate(version, platform, args) {
    def document = Jsoup.parse('https://www.videolan.org/vlc/download-windows.html'.toURL(), 30000)
    if (!document) {
        return null
    }
    version = document.selectFirst('span#downloadVersion').text()
    return [
            'version': version,
            'url'    : [
                    '7zip package'                 : "https://get.videolan.org/vlc/${version}/win32/vlc-${version}-win32.7z".toString(),
                    'Zip package'                  : "https://get.videolan.org/vlc/${version}/win32/vlc-${version}-win32.zip".toString(),
                    'MSI package'                  : "https://get.videolan.org/vlc/${version}/win32/vlc-${version}-win32.msi".toString(),
                    'Installer for 64bit version'  : "https://get.videolan.org/vlc/${version}/win64/vlc-${version}-win64.exe".toString(),
                    'MSI package for 64bit version': "https://get.videolan.org/vlc/${version}/win64/vlc-${version}-win64.msi".toString(),
            ]
    ]
}