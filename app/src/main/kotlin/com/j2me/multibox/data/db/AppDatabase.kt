package com.j2me.multibox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [GameEntity::class, BoxEntity::class, AccountEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun boxDao(): BoxDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "vault_meta_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            var passHex = prefs.getString("db_salt", null)
            if (passHex == null) {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                passHex = bytes.joinToString("") { "%02x".format(it) }
                prefs.edit().putString("db_salt", passHex).apply()
            }
            val factory = SupportFactory(passHex.toByteArray(Charsets.UTF_8))

            return Room.databaseBuilder(context, AppDatabase::class.java, "j2me_multibox.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
