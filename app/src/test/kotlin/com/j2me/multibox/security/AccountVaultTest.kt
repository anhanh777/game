package com.j2me.multibox.security

import com.j2me.multibox.data.db.AccountDao
import com.j2me.multibox.data.db.AccountEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AccountVaultTest {

    private class FakeCryptoProvider : CryptoProvider {
        override fun encrypt(plain: CharArray): EncryptedPayload {
            val bytes = String(plain).toByteArray(Charsets.UTF_8).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            return EncryptedPayload(
                cipherTextBase64 = Base64.getEncoder().encodeToString(bytes),
                ivBase64 = Base64.getEncoder().encodeToString(ByteArray(12) { 1 })
            )
        }

        override fun decrypt(payload: EncryptedPayload): CharArray {
            val bytes = Base64.getDecoder().decode(payload.cipherTextBase64).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            return String(bytes, Charsets.UTF_8).toCharArray()
        }
    }

    @Test
    fun test_credentials_encrypted_on_save_and_zeroed_after_use() = runTest {
        val dao = mockk<AccountDao>(relaxed = true)
        val capturedEntity = slot<AccountEntity>()
        coEvery { dao.upsertAccount(capture(capturedEntity)) } returns Unit

        val vault = AccountVault(dao, FakeCryptoProvider())
        val user = "ninja_acc1".toCharArray()
        val pass = "SuperSecret123!".toCharArray()

        vault.saveCredentials("box-1", user, pass, "Main farm acc")

        // Verify input buffers were wiped after saving
        assertTrue(user.all { it == '\u0000' })
        assertTrue(pass.all { it == '\u0000' })

        // Verify stored entity does not contain plaintext
        assertFalse(capturedEntity.captured.encryptedUsername.contains("ninja_acc1"))
        assertFalse(capturedEntity.captured.encryptedPassword.contains("SuperSecret123!"))

        // Stub DAO read
        coEvery { dao.getAccountByBoxId("box-1") } returns capturedEntity.captured

        var seenUser: CharArray? = null
        var seenPass: CharArray? = null

        vault.withDecryptedCredentials("box-1") { u, p ->
            assertArrayEquals("ninja_acc1".toCharArray(), u)
            assertArrayEquals("SuperSecret123!".toCharArray(), p)
            seenUser = u
            seenPass = p
        }

        // Verify credentials were zeroed out from memory immediately after block finished
        assertTrue(seenUser!!.all { it == '\u0000' })
        assertTrue(seenPass!!.all { it == '\u0000' })
        coVerify { dao.upsertAccount(any()) }
    }

    @Test
    fun test_credentials_zeroed_even_when_block_throws() = runTest {
        val dao = mockk<AccountDao>(relaxed = true)
        val crypto = FakeCryptoProvider()
        val encU = crypto.encrypt("user1".toCharArray())
        val encP = crypto.encrypt("pass1".toCharArray())

        coEvery { dao.getAccountByBoxId("box-1") } returns AccountEntity(
            boxId = "box-1",
            encryptedUsername = encU.cipherTextBase64,
            usernameIv = encU.ivBase64,
            encryptedPassword = encP.cipherTextBase64,
            passwordIv = encP.ivBase64,
            note = ""
        )

        val vault = AccountVault(dao, crypto)
        var capturedU: CharArray? = null
        var capturedP: CharArray? = null

        try {
            vault.withDecryptedCredentials("box-1") { u, p ->
                capturedU = u
                capturedP = p
                throw RuntimeException("Macro aborted")
            }
        } catch (_: RuntimeException) {
            // Expected
        }

        assertTrue(capturedU!!.all { it == '\u0000' })
        assertTrue(capturedP!!.all { it == '\u0000' })
    }
}
