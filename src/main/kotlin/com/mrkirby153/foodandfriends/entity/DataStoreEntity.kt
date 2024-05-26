package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

@Entity
@Table(name = "data_store")
class DataStoreEntity(
    @Id
    @Column(name = "id")
    val id: String = generateUlid(),
    @Column(name = "data_store_id")
    val dataStoreId: String? = null,
    @Column(name = "key")
    var key: String? = null,
    @Column(name = "value")
    var value: ByteArray? = null,
)


interface DataStoreEntityRepository : JpaRepository<DataStoreEntity, String> {

    fun countAllByDataStoreId(dataStoreId: String): Long

    fun existsByDataStoreIdAndKey(dataStoreId: String, key: String): Boolean

    @Query("SELECT a.key FROM DataStoreEntity as a WHERE a.dataStoreId = (:dataStoreId)")
    fun getAllKeys(dataStoreId: String): List<String>

    @Query("SELECT a.value FROM DataStoreEntity as a WHERE a.dataStoreId = (:dataStoreId)")
    fun getAllValues(dataStoreId: String): List<ByteArray>

    fun getByDataStoreIdAndKey(dataStoreId: String, key: String): DataStoreEntity?

    fun deleteAllByDataStoreId(dataStoreId: String)

    fun deleteByDataStoreIdAndKey(dataStoreId: String, key: String)

    fun existsByDataStoreIdAndValue(dataStoreId: String, value: ByteArray): Boolean
}