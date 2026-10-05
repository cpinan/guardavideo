package com.carlospinan.guardavideo.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailureTest {
    @Test
    fun realMessagesMapToTheirCause() {
        assertEquals(FailureKind.Forbidden, classifyFailure("[dailymotion] xb3sqwa: Access forbidden."))
        assertEquals(FailureKind.Removed, classifyFailure("ERROR: [youtube] abc: Video unavailable"))
        assertEquals(FailureKind.Removed, classifyFailure("El video ya no existe en el origen (404)"))
        assertEquals(FailureKind.LoginRequired, classifyFailure("Sign in to confirm you're not a bot"))
        assertEquals(FailureKind.GeoBlocked, classifyFailure("The uploader has not made this video available in your country"))
        assertEquals(FailureKind.Network, classifyFailure("Sin conexión con el origen"))
        assertEquals(FailureKind.Drm, classifyFailure("Cifrado no soportado (SAMPLE-AES): puede estar protegido con DRM"))
        assertEquals(FailureKind.NoSpace, classifyFailure("OSError: [Errno 28] No space left on device"))
    }

    @Test
    fun unknownErrorsAreNotGivenAnInventedCause() {
        assertEquals(FailureKind.Unknown, classifyFailure("something odd"))
        assertEquals(FailureKind.Unknown, classifyFailure(null))
        assertTrue(FailureKind.Unknown.retryable)
        assertFalse(FailureKind.Drm.retryable)
    }
}
