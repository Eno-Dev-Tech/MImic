package com.example.mimic

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.rpc.Status
import com.google.type.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android runtime compatibility; no account or network access. */
@RunWith(AndroidJUnit4::class)
class FirestoreProtoDeviceTest {
    @Test fun firestore_shared_types_serialize_on_android() {
        val point = LatLng.newBuilder().setLatitude(47.4979).setLongitude(19.0402).build()
        assertEquals(point, LatLng.parseFrom(point.toByteArray()))
        val status = Status.newBuilder().setCode(7).setMessage("fixture").build()
        assertEquals(status, Status.parseFrom(status.toByteArray()))
    }
}
