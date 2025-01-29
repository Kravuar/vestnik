package net.kravuar.vestnik.channels

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

enum class ChannelPlatform {
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
    @get:NotBlank
    var name: String,
    @Column(nullable = false)
    var platform: ChannelPlatform,
    @Column(nullable = false)
    var deleted: Boolean = false,
) {
    override fun toString(): String {
        return "Channel(id=$id, name='$name', platform=$platform, deleted=$deleted)"
    }
}