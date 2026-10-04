package com.carlospinan.guardavideo.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
actual fun platformDecryptor(): SegmentDecryptor = SegmentDecryptor { data, key, iv ->
    if (data.isEmpty()) return@SegmentDecryptor data
    val out = ByteArray(data.size + 16)
    memScoped {
        val moved = alloc<size_tVar>()
        val status = data.usePinned { d ->
            key.usePinned { k ->
                iv.usePinned { v ->
                    out.usePinned { o ->
                        CCCrypt(
                            kCCDecrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding,
                            k.addressOf(0), key.size.convert(), v.addressOf(0),
                            d.addressOf(0), data.size.convert(), o.addressOf(0), out.size.convert(), moved.ptr,
                        )
                    }
                }
            }
        }
        if (status != kCCSuccess) throw DownloadException("No se pudo descifrar el segmento ($status)")
        out.copyOf(moved.value.toInt())
    }
}
