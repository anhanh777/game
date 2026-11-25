package com.j2me.multibox.security

import com.j2me.multibox.data.db.AccountDao
import com.j2me.multibox.data.db.AccountEntity

/**
 * Encrypted local Account Vault.
 * Credentials are stored as AES-256-GCM ciphertext and wiped from RAM immediately after injection.
 */
class AccountVault(
    private val accountDao: AccountDao,
    private val crypto: CryptoProvider
) {

    suspend fun saveCredentials(
        boxId: String,
        username: CharArray,
        password: CharArray,
        note: String = ""
    ) {
        try {
            val encUser = crypto.encrypt(username)
            val encPass = crypto.encrypt(password)

            accountDao.upsertAccount(
                AccountEntity(
                    boxId = boxId,
                    encryptedUsername = encUser.cipherTextBase64,
                    usernameIv = encUser.ivBase64,
                    encryptedPassword = encPass.cipherTextBase64,
                    passwordIv = encPass.ivBase64,
                    note = note
                )
            )
        } finally {
            username.fill('\u0000')
            password.fill('\u0000')
        }
    }

    suspend fun withDecryptedCredentials(
        boxId: String,
        block: suspend (username: CharArray, password: CharArray) -> Unit
    ): Boolean {
        val entity = accountDao.getAccountByBoxId(boxId) ?: return false
        val username = crypto.decrypt(EncryptedPayload(entity.encryptedUsername, entity.usernameIv))
        val password = crypto.decrypt(EncryptedPayload(entity.encryptedPassword, entity.passwordIv))

        try {
            block(username, password)
        } finally {
            username.fill('\u0000')
            password.fill('\u0000')
        }
        return true
    }

    suspend fun hasCredentials(boxId: String): Boolean {
        return accountDao.getAccountByBoxId(boxId) != null
    }
}
