package com.mrkirby153.foodandfriends.entity

import org.springframework.data.util.ProxyUtils
import java.lang.reflect.Field
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.MappedSuperclass

@MappedSuperclass
abstract class AbstractJpaEntity<T : java.io.Serializable>(@Id open val id: T? = null) : BaseAbstractJpaEntity<T>() {

    override fun equals(other: Any?): Boolean {
        other ?: return false
        if (this === other) return true

        if (javaClass != ProxyUtils.getUserClass(other)) return false
        other as AbstractJpaEntity<*>
        return if (null == this.id) false else this.id == other.id
    }

    override fun hashCode(): Int {
        return this.javaClass.hashCode()
    }
}

abstract class AbstractAutogeneratedJpaEntity<T : java.io.Serializable> : AbstractJpaEntity<T>() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: T? = null
}

abstract class BaseAbstractJpaEntity<T : java.io.Serializable> {
    override fun toString(): String {
        val targetInstance = this

        val fields = fieldCache.computeIfAbsent(javaClass) {
            val fields = mutableListOf<Field>()
            var clazz: Class<*>? = javaClass
            while (clazz != null) {
                fields.addAll(clazz.declaredFields)
                clazz = clazz.superclass
            }
            fields
        }

        return buildString {
            append("${targetInstance.javaClass.canonicalName}{")
            append(fields.joinToString(", ") {
                try {
                    it.trySetAccessible()
                    "${it.name}=${it.get(targetInstance)}"
                } catch (ignored: SecurityException) {
                    ""
                }
            })
            append("}")
        }
    }

    companion object {
        private val fieldCache = mutableMapOf<Class<*>, List<Field>>()
    }
}