package net.kravuar.vestnik.channels

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import net.kravuar.vestnik.source.Source

enum class ChannelPlatform {
    VK,
    TG
}

@Entity
@Table(indexes = [
    Index(columnList = "id,platform", unique = true)
])
class Channel(
    @Id
    @Column(nullable = false)
    var id: Long,
    @Column(nullable = false, unique = true)
    @NotBlank
    var name: String,
    @Column(nullable = false)
    var platform: ChannelPlatform,
    @Column(nullable = false)
    var deleted: Boolean = false,
    sources: MutableSet<Source> = HashSet(),
) {
    override fun toString(): String {
        return "Channel(id=$id, name='$name', platform=$platform, deleted=$deleted)"
    }

    @ManyToMany(mappedBy = "channels", fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var sources: MutableSet<Source> = sources
        set(newSources) {
            // Remove this channel from the old sources that are no longer in the new set
            sources.forEach { oldSource ->
                if (!newSources.contains(oldSource)) {
                    oldSource.channels.remove(this)
                }
            }

            // Add this channel to the new sources
            newSources.forEach { newSource ->
                if (!sources.contains(newSource)) {
                    newSource.channels.add(this)
                }
            }

            // Update the backing property with the new sources
            field = newSources
        }

}