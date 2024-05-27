package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "schedule_order")
class Order(
    @Column(name = "name")
    var name: String = "New Order"
) {
    @Id
    @Column(name = "id")
    val id: String = generateUlid()

    @Column(name = "\"order\"")
    private var order: String? = null

    @OneToOne(mappedBy = "order")
    var schedule: Schedule? = null


    fun addPerson(person: Person) {
        addPerson(person.id)
    }

    fun addPerson(id: String) {
        val people = getPeopleIds().toMutableList()
        if (id in people)
            return
        people.add(id)
        setOrder(people)
    }

    fun removePerson(person: Person) {
        removePerson(person.id)
    }

    fun removePerson(id: String) {
        val people = getPeopleIds().toMutableList()
        people.remove(id)
        setOrder(people)
    }

    fun getPeopleIds(): List<String> {
        return order?.split(",") ?: emptyList()
    }

    fun getPeopleAsString(): String {
        return order ?: ""
    }

    private fun setOrder(people: List<String>) {
        this.order = people.joinToString(",")
    }
}

interface OrderRepository : JpaRepository<Order, String> {

}