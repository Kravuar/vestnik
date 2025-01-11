package net.kravuar.vestnik.channels

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.validation.constraints.NotBlank
import net.kravuar.vestnik.source.Source

enum class ChannelPlatform {
    VK,
    TG
}

@Entity
class Channel(
    @Id
    @Column(nullable = false)
    var id: Long,
    @Column(nullable = false, unique = true)
    @NotBlank
    var name: String,
    @Column(nullable = false)
    var platform: ChannelPlatform,
    @ManyToMany(mappedBy = "channels")
    var sources: MutableSet<Source> = HashSet(),
    @Column(nullable = false)
    var deleted: Boolean = false,
) {
    override fun toString(): String {
        return "Channel(id=$id, name='$name', platform=$platform, sources=$sources, deleted=$deleted)"
    }
}