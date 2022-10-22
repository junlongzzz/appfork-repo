import cn.hutool.core.date.DateTime
import cn.hutool.core.date.DateUtil
import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def v = null
    def u = [:]

    def response = 'https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=zh-CN&country=CN&allowCountries=CN'.toURL().text
    def object = new JsonSlurper().parseText(response)
    def searchStore = object.data.Catalog.searchStore
    if (searchStore.paging.total <= 0) {
        return null
    }
    def elements = searchStore.elements
    for (element in elements) {
        if (element.price == null) {
            continue
        }
        // 折扣价为0
        if (element.price.totalPrice.discountPrice <= 0) {
            def flag = false
            // 付款规则（优惠）
            def offers = []
            // 折扣
            offers.addAll(element.price.lineOffers)
            if (element.promotions != null) {
                // 促销
                offers.addAll(element.promotions.promotionalOffers)
            }
            for (offer in offers) {
                // 规则
                def rules = offer.appliedRules
                if (rules == null) {
                    rules = offer.promotionalOffers
                }
                for (rule in rules) {
                    // 折扣优惠结束时间在今日之后表示正在进行优惠折扣
                    if (rule.endDate) {
                        def endDate = DateUtil.parse(rule.endDate).setTimeZone(TimeZone.getDefault())
                        def nowDate = new Date()
                        if (endDate.isAfter(nowDate)) {
                            if (v == null) {
                                v = endDate
                            } else if (endDate.isAfter(v)) {
                                v = endDate
                            }
                            // 游戏商城链接
                            String slug
                            if (element.catalogNs.mappings) {
                                slug = element.catalogNs.mappings[0].pageSlug
                            } else {
                                slug = element.productSlug
                            }
                            u[element.title as String] = "https://store.epicgames.com/zh-CN/p/${slug}".toString()
                            flag = true
                            break
                        }
                    }
                }
                if (flag) {
                    break
                }
            }
        }
    }

    if (v) {
        return [
                'version': v.toString(),
                'url'    : u
        ]
    }

    return null
}
