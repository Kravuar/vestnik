package net.kravuar.vestnik.destination

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

enum class DestinationType {
    VK {
        // Does not support any of those
        override fun boldOpenTag(): String = ""
        override fun boldCloseTag(): String = ""

        override fun cursiveOpenTag(): String = ""
        override fun cursiveCloseTag(): String = ""

        override fun underscoreOpenTag(): String = ""
        override fun underscoreCloseTag(): String = ""

        override fun linkOpenTagStart(): String = ""
        override fun linkOpenTagHref(): String = ""
        override fun linkOpenTagEnd(): String = ""
        override fun linkCloseTag(): String = ""
    },
    TELEGRAM;

    open fun boldOpenTag(): String = "<b>"
    open fun boldCloseTag(): String = "</b>"

    open fun cursiveOpenTag(): String = "<i>"
    open fun cursiveCloseTag(): String = "</i>"

    open fun underscoreOpenTag(): String = "<u>"
    open fun underscoreCloseTag(): String = "</u>"

    open fun linkOpenTagStart(): String = "<a"
    open fun linkOpenTagHref(): String = "href=\""
    open fun linkOpenTagEnd(): String = "\">"
    open fun linkCloseTag(): String = "</a>"
}

@Entity
class Destination(
    @Id
    var id: String,
    @Column(nullable = false, unique = true)
    var name: String,
    @Column(nullable = false)
    var destinationType: DestinationType,
)