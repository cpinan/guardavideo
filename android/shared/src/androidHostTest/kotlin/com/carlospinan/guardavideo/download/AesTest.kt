package com.carlospinan.guardavideo.download

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AesTest {
    @Test
    fun decryptsWhatAes128CbcPkcs7Encrypted() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { (15 - it).toByte() }
        val plain = "segmento de video TS de prueba, más de un bloque".encodeToByteArray()
        val encrypted = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(plain)
        }
        assertContentEquals(plain, platformDecryptor().decrypt(encrypted, key, iv))
    }
}
