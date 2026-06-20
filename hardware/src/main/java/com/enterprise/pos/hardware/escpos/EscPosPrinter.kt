package com.enterprise.pos.hardware.escpos

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.enterprise.pos.hardware.printer.PrinterConnectionType
import com.enterprise.pos.hardware.printer.PrinterInfo
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket

/**
 * Abstraction over an ESC/POS thermal printer transport.
 * Implementations handle USB, Bluetooth RFCOMM, and TCP network (port 9100) connections.
 */
interface EscPosPrinter {
    val info: PrinterInfo
    val isConnected: Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun print(data: ByteArray): Result<Unit>
}

/**
 * USB ESC/POS printer using Android [UsbManager].
 *
 * Supports most USB thermal printers that present a bulk-out endpoint (Epson, Star, Bixolon).
 */
class UsbEscPosPrinter(
    private val usbManager: android.hardware.usb.UsbManager,
    private val usbDevice: android.hardware.usb.UsbDevice
) : EscPosPrinter {

    override val info: PrinterInfo = PrinterInfo(
        id = "usb-${usbDevice.deviceId}",
        name = usbDevice.productName ?: "USB Printer",
        model = usbDevice.productName ?: "Unknown",
        connectionType = PrinterConnectionType.USB,
        address = usbDevice.deviceName,
        isConnected = false
    )

    private var connection: android.hardware.usb.UsbDeviceConnection? = null

    override val isConnected: Boolean
        get() = connection != null

    override suspend fun connect(): Result<Unit> = Result.catching {
        if (usbManager.hasPermission(usbDevice)) {
            connection = usbManager.openDevice(usbDevice)
                ?: throw IllegalStateException("Failed to open USB device ${usbDevice.deviceId}")
        } else {
            throw IllegalStateException("USB permission not granted for ${usbDevice.deviceId}")
        }
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        connection?.close()
        connection = null
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        val conn = requireNotNull(connection) { "USB printer not connected" }
        val usbInterface = usbDevice.getInterface(0)
        val endpoint = usbInterface.getEndpoint(0)
        requireNotNull(endpoint) { "USB printer has no endpoint" }

        conn.claimInterface(usbInterface, true)
        try {
            val written = conn.bulkTransfer(endpoint, data, data.size, 5000)
            if (written < 0) {
                throw IllegalStateException("USB bulk transfer failed: $written")
            }
        } finally {
            conn.releaseInterface(usbInterface)
        }
    }
}

/**
 * Bluetooth ESC/POS printer using Android [BluetoothAdapter] and RFCOMM.
 *
 * Supports printers with a standard SPP (Serial Port Profile) UUID.
 */
class BluetoothEscPosPrinter(
    private val bluetoothAdapter: BluetoothAdapter,
    private val macAddress: String
) : EscPosPrinter {

    override val info: PrinterInfo = PrinterInfo(
        id = "bt-${macAddress.replace(":", "")}",
        name = "Bluetooth Printer",
        model = "generic-bt",
        connectionType = PrinterConnectionType.BLUETOOTH,
        address = macAddress,
        isConnected = false
    )

    private var socket: android.bluetooth.BluetoothSocket? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    override suspend fun connect(): Result<Unit> = Result.catching {
        require(macAddress.isNotBlank()) { "Bluetooth MAC address is required" }
        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        socket = device.createRfcommSocketToServiceRecord(sppUuid)
        socket?.connect()
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        socket?.close()
        socket = null
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        val activeSocket = requireNotNull(socket?.takeIf { it.isConnected }) {
            "Bluetooth printer is not connected"
        }
        activeSocket.outputStream.apply {
            write(data)
            flush()
        }
    }
}

/**
 * Network ESC/POS printer over TCP port 9100 (raw port).
 *
 * Supports Epson ePOS, Star webPRNT, and most networked thermal printers.
 */
class NetworkEscPosPrinter(
    private val host: String,
    private val port: Int = 9100
) : EscPosPrinter {

    override val info: PrinterInfo = PrinterInfo(
        id = "net-$host-$port",
        name = "Network Printer ($host)",
        model = "generic-network",
        connectionType = PrinterConnectionType.NETWORK,
        address = "$host:$port",
        isConnected = false
    )

    private var socket: Socket? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect(): Result<Unit> = Result.catching {
        require(host.isNotBlank()) { "Printer host is required" }
        require(port in 1..65535) { "Printer port must be in 1..65535" }
        socket?.close()
        socket = Socket().apply {
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            tcpNoDelay = true
        }
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        socket?.close()
        socket = null
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        val activeSocket = requireNotNull(socket?.takeIf { it.isConnected && !it.isClosed }) {
            "Network printer is not connected"
        }
        activeSocket.getOutputStream().apply {
            write(data)
            flush()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
    }
}

/**
 * Simulated ESC/POS printer for development and testing.
 * Logs all print data but does not send to any physical device.
 */
class SimulatedEscPosPrinter(
    private val logger: com.enterprise.pos.core.Logger
) : EscPosPrinter {

    override val info: PrinterInfo = PrinterInfo(
        id = "sim-printer",
        name = "Simulated ESC/POS Printer",
        model = "simulated",
        connectionType = PrinterConnectionType.NETWORK,
        address = "127.0.0.1:9100",
        isConnected = true
    )

    private var _connected = true

    override val isConnected: Boolean get() = _connected

    override suspend fun connect(): Result<Unit> = Result.catching {
        _connected = true
        logger.i(TAG, "Simulated printer connected")
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        _connected = false
        logger.i(TAG, "Simulated printer disconnected")
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        logger.i(TAG, "[SIMULATED] Print ${data.size} bytes")
    }

    companion object {
        private const val TAG = "SimulatedEscPosPrinter"
    }
}
