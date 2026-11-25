package com.j2me.multibox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE gameId = :gameId")
    suspend fun getGame(gameId: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(game: GameEntity)

    @Query("DELETE FROM games WHERE gameId = :gameId")
    suspend fun deleteGame(gameId: String)
}

@Dao
interface BoxDao {
    @Query("SELECT * FROM boxes ORDER BY createdAt ASC")
    fun observeBoxes(): Flow<List<BoxEntity>>

    @Query("SELECT * FROM boxes WHERE boxId = :boxId")
    suspend fun getBox(boxId: String): BoxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBox(box: BoxEntity)

    @Query("UPDATE boxes SET name = :newName WHERE boxId = :boxId")
    suspend fun renameBox(boxId: String, newName: String)

    @Query("DELETE FROM boxes WHERE boxId = :boxId")
    suspend fun deleteBox(boxId: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE boxId = :boxId")
    suspend fun getAccountByBoxId(boxId: String): AccountEntity?

    @Query("SELECT * FROM accounts")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE boxId = :boxId")
    suspend fun deleteAccount(boxId: String)
}
