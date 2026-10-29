package com.aiusage.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiusage.app.data.crypto.SecretCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretCipherTest {
    @Test
    fun encryptsAndDecryptsWithKeystore() {
        val cipher = SecretCipher()
        val encrypted = cipher.encrypt("sk-kimi-test-key")
        assertNotEquals("sk-kimi-test-key", encrypted)
        assertEquals("sk-kimi-test-key", cipher.decrypt(encrypted))
    }

    @Test
    fun rejectsCorruptCiphertext() {
        val cipher = SecretCipher()
        assertNull(cipher.decrypt("not-base64"))
        assertNull(cipher.decrypt(""))
    }

    @Test
    fun targetPackageIsDebugBuild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aiusage.app.debug", context.packageName)
    }
}
