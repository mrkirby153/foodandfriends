package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "messages")
class SentAnnouncementMessage(
    @Id
    @GeneratedValue
    var id: Long = 0,
    var messageId: String = "",
    var channelId: String = ""
);

interface SentMessageRepo : JpaRepository<SentAnnouncementMessage, Long> {

    fun getFirstByMessageId(id: String): SentAnnouncementMessage?
}