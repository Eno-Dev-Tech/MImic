package com.example.mimic

import com.google.rpc.Status
import com.google.type.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Full protobuf deduplication must retain Firestore's non-protobuf types. */
class FirestoreProtoTest {
    @Test fun geographic_coordinates_round_trip_with_the_full_protobuf_runtime() {
        val value = LatLng.newBuilder().setLatitude(47.4979).setLongitude(19.0402).build()
        val restored = LatLng.parseFrom(value.toByteArray())
        assertEquals(47.4979, restored.latitude, 0.000001)
        assertEquals(19.0402, restored.longitude, 0.000001)
    }

    @Test fun rpc_status_round_trips_without_losing_its_message() {
        val value = Status.newBuilder().setCode(7).setMessage("permission denied fixture").build()
        val restored = Status.parseFrom(value.toByteArray())
        assertEquals(7, restored.code)
        assertEquals("permission denied fixture", restored.message)
    }
}
