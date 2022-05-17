package com.mrkirby153.foodandfriends.entity

import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

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