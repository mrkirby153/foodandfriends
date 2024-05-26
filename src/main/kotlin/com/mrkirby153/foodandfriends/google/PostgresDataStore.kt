package com.mrkirby153.foodandfriends.google

import com.google.api.client.util.IOUtils
import com.google.api.client.util.store.DataStore
import com.google.api.client.util.store.DataStoreFactory
import com.mrkirby153.foodandfriends.entity.DataStoreEntity
import com.mrkirby153.foodandfriends.entity.DataStoreEntityRepository
import java.io.Serializable

class PostgresDataStore<V : Serializable?>(
    private val dataStoreEntityRepository: DataStoreEntityRepository,
    private val factory: DataStoreFactory,
    private val id: String
) : DataStore<V> {

    override fun getDataStoreFactory(): DataStoreFactory {
        return factory
    }

    override fun getId(): String {
        return id
    }

    override fun size(): Int {
        return dataStoreEntityRepository.countAllByDataStoreId(this.id).toInt()
    }

    override fun isEmpty(): Boolean {
        return this.size() == 0
    }

    override fun containsKey(key: String): Boolean {
        return dataStoreEntityRepository.existsByDataStoreIdAndKey(this.id, key)
    }

    override fun keySet(): MutableSet<String> {
        return dataStoreEntityRepository.getAllKeys(this.id).toMutableSet()
    }

    override fun values(): MutableCollection<V> {
        return dataStoreEntityRepository.getAllValues(this.id).map { deserialize(it) }
            .toMutableList()
    }

    override fun get(key: String): V? {
        return dataStoreEntityRepository.getByDataStoreIdAndKey(this.id, key)
            ?.let { deserialize(it.value ?: ByteArray(0)) }
    }

    override fun clear(): DataStore<V> {
        dataStoreEntityRepository.deleteAllByDataStoreId(this.id)
        return this
    }

    override fun delete(key: String): DataStore<V> {
        dataStoreEntityRepository.deleteByDataStoreIdAndKey(this.id, key)
        return this
    }

    override fun set(key: String, value: V): DataStore<V> {
        val existing = dataStoreEntityRepository.getByDataStoreIdAndKey(this.id, key)
        if (existing != null) {
            existing.value = serialize(value)
            dataStoreEntityRepository.save(existing)
        } else {
            dataStoreEntityRepository.save(
                DataStoreEntity(
                    dataStoreId = this.id,
                    key = key,
                    value = serialize(value)
                )
            )
        }
        return this
    }

    override fun containsValue(value: V): Boolean {
        return dataStoreEntityRepository.existsByDataStoreIdAndValue(this.id, serialize(value))
    }

    private fun deserialize(bytes: ByteArray): V {
        return IOUtils.deserialize(bytes)
    }

    private fun serialize(obj: V): ByteArray {
        return IOUtils.serialize(obj)
    }
}

class PostgresDataStoreFactory(
    private val dataStoreEntityRepository: DataStoreEntityRepository,
    private val id: String
) : DataStoreFactory {

    override fun <V : Serializable?> getDataStore(id: String): DataStore<V> {
        return PostgresDataStore(dataStoreEntityRepository, this, id)
    }

}