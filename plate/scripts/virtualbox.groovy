import org.jsoup.Jsoup
import org.jsoup.nodes.Element

static def checkUpdate(manifest, args) {
    def document = Jsoup.parse('https://www.virtualbox.org/wiki/Downloads'.toURL(), 60000)
    if (!document) {
        return null
    }

    def version = null
    def url = []
    for (Element element : document.select('ul a.ext-link[href]')) {
        def href = element.attr('href')
        if (href.endsWith('.exe')) {
            def matcher = href =~ '/(?<version>[\\d.]+)/'
            if (!matcher.find()) {
                return null
            }
            version = matcher.group('version')
            url << href
        } else if (href.endsWith('.vbox-extpack')) {
            url << href
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}