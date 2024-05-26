package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "messages")
class SentAnnouncementMessage(
    @Id
    var id: String = generateUlid(),
    var messageId: String = "",
    var channelId: String = ""
);

interface SentMessageRepo : JpaRepository<SentAnnouncementMessage, Long> {

    fun getFirstByMessageId(id: String): SentAnnouncementMessage?
}