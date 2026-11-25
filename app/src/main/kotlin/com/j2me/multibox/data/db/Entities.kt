package com.j2me.multibox.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val gameId: String,
    val title: String,
    val jarPath: String,
    val jadPath: String? = null,
    val loginMacroJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "boxes",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class BoxEntity(
    @PrimaryKey val boxId: String,
    val name: String,
    val gameId: String,
    val note: String = "",
    val autoReconnect: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["boxId"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AccountEntity(
    @PrimaryKey val boxId: String,
    val encryptedUsername: String,
    val usernameIv: String,
    val encryptedPassword: String,
    val passwordIv: String,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
