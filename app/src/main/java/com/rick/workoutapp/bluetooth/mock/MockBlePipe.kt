package com.rick.workoutapp.bluetooth.mock

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * In-memory stand-in for the BLE radio between phone (client) and machine (server).
 * Real BLE would deliver the same notification bytes via [BluetoothGattCallback].
 */
class MockBlePipe {

    private val _notifications = MutableSharedFlow<GattNotification>(
        extraBufferCapacity = 64,
    )
    val notifications: SharedFlow<GattNotification> = _notifications.asSharedFlow()

    private val _controlWrites = MutableSharedFlow<GattWrite>(
        extraBufferCapacity = 8,
    )
    val controlWrites: SharedFlow<GattWrite> = _controlWrites.asSharedFlow()

    suspend fun notify(characteristicUuid: UUID, value: ByteArray) {
        _notifications.emit(GattNotification(characteristicUuid, value.copyOf()))
    }

    suspend fun write(characteristicUuid: UUID, value: ByteArray) {
        _controlWrites.emit(GattWrite(characteristicUuid, value.copyOf()))
    }
}

data class GattNotification(
    val characteristicUuid: UUID,
    val value: ByteArray,
)

data class GattWrite(
    val characteristicUuid: UUID,
    val value: ByteArray,
)
