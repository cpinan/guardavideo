package com.carlospinan.guardavideo.download

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// "PKCS5Padding" in the JCA is PKCS#7 for 16-byte blocks, which is what HLS uses.
actual fun platformDecryptor(): SegmentDecryptor = SegmentDecryptor { data, key, iv ->
    Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        doFinal(data)
    }
}
