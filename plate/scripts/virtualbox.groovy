import org.jsoup.Jsoup
import org.jsoup.nodes.Element

static def checkUpdate(version, platform, args) {
    def document = Jsoup.parse('https://www.virtualbox.org/wiki/Downloads'.toURL(), 30000)
    if (!document) {
        return null
    }

    def url = [:]
    boolean done = false
    for (Element element : document.select('ul a.ext-link[href]')) {
        println(element)
        def href = element.attr('href')
        if (href.endsWith('.exe')) {
            def matcher = href =~ '/([\\d.]+)/'
            if (!matcher.find()) {
                return null
            }
            version = matcher[0][1] as String
            url[href.substring(href.lastIndexOf('/') + 1)] = href
        } else if (href.endsWith('.vbox-extpack')) {
            url[href.substring(href.lastIndexOf('/') + 1)] = href
        }
        if (url.size() == 2) {
            done = true
            break
        }
    }

    if (!done) {
        return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}