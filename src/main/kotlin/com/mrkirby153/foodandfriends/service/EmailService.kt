package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Email
import com.mrkirby153.foodandfriends.entity.EmailRepo
import jakarta.transaction.Transactional
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service

interface EmailService {

    fun getEmail(user: User): String?

    fun setEmail(user: User, email: String)

    fun removeEmail(user: User)
}

@Service
class EmailManager(
    private val emailRepo: EmailRepo
) : EmailService {
    override fun getEmail(user: User) = emailRepo.findByUserId(user.id)?.email

    override fun setEmail(user: User, email: String) {
        val existing = emailRepo.findByUserId(user.id)
        if (existing != null) {
            existing.email = email
            emailRepo.save(existing)
        } else {
            emailRepo.save(Email(user, email))
        }
    }

    @Transactional
    override fun removeEmail(user: User) {
        emailRepo.deleteByUserId(user.id)
    }

}