package net.kravuar.vestnik.destination

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.validation.constraints.NotBlank
import net.kravuar.vestnik.source.Source

enum class ChannelPlatform {
    VK {
        // Does not support any of those
        override fun b1(): String = ""
        override fun b2(): String = ""

        override fun i1(): String = ""
        override fun i2(): String = ""

        override fun u1(): String = ""
        override fun u2(): String = ""

        override fun l1(): String = ""
        override fun l2(): String = ""
        override fun l3(): String = ""
        override fun l4(): String = ""
    },
    TELEGRAM;

    open fun b1(): String = "<b>"
    open fun b2(): String = "</b>"

    open fun i1(): String = "<i>"
    open fun i2(): String = "</i>"

    open fun u1(): String = "<u>"
    open fun u2(): String = "</u>"

    open fun l1(): String = "<a"
    open fun l2(): String = "href=\""
    open fun l3(): String = "\">"
    open fun l4(): String = "</a>"
}

@Entity
class Channel(
    @Id
    @NotBlank
    var id: String,
    @Column(nullable = false, unique = true)
    @NotBlank
    var name: String,
    @Column(nullable = false)
    var platform: ChannelPlatform,
    @ManyToMany(mappedBy = "channels")
    var sources: MutableSet<Source> = HashSet(),
    @Column(nullable = false)
    var deleted: Boolean = false,
)