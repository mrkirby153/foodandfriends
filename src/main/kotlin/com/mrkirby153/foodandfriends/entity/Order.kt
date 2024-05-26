package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid

@Entity
@Table(name = "schedule_order")
class Order {
    @Id
    @Column(name = "id")
    val id: String = generateUlid()

    @Column(name = "order")
    private var orderBackingField: String? = null

    var order: List<String>
        get() = orderBackingField?.split(",") ?: emptyList()
        set(value) {
            orderBackingField = value.joinToString(",")
        }
}