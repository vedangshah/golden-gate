// Copyright 2017-2020 Fitbit, Inc
// SPDX-License-Identifier: Apache-2.0

package com.fitbit.linkcontroller

import android.bluetooth.BluetoothDevice
import com.fitbit.bluetooth.fbgatt.FitbitBluetoothDevice
import com.fitbit.bluetooth.fbgatt.FitbitGatt
import com.fitbit.bluetooth.fbgatt.GattConnection
import com.fitbit.bluetooth.fbgatt.rx.server.BitGattServer
import com.fitbit.linkcontroller.services.configuration.LinkConfigurationService
import com.fitbit.linkcontroller.services.configuration.LinkConfigurationServiceEventListener
import io.reactivex.Completable

/**
 * This class initialises the Link Controller Configuration Service.
 * It provides to the consumers [LinkController] objects based on [FitbitBluetoothDevice]
 * These are used to configure connection parameters between the mobile app and the peripheral device
 */

class LinkControllerProvider private constructor (
    private val fitbitGatt: FitbitGatt = FitbitGatt.getInstance(),
    private val gattServer: BitGattServer = BitGattServer(),
    private val linkConfigurationService: LinkConfigurationService = LinkConfigurationService(),
    private val linkConfigurationServiceEventListener: LinkConfigurationServiceEventListener = LinkConfigurationServiceEventListener()
) {
    private val linkControllersMap = hashMapOf<BluetoothDevice, LinkController>()

    private object Holder {
        val INSTANCE = LinkControllerProvider()
    }


    companion object {
        val INSTANCE: LinkControllerProvider by lazy { Holder.INSTANCE }
    }

    init {
        linkConfigurationServiceEventListener.linkControllerProvider = this
    }

    /**
     * Get the Link controller for a specific peripheral device
     */
    @Synchronized
    fun getLinkController(device: BluetoothDevice): LinkController? {
        return linkControllersMap[device] ?: add(device)
    }

    @Synchronized
    fun getLinkController(gattConnection: GattConnection): LinkController {
        return linkControllersMap[gattConnection.device.btDevice] ?: add(gattConnection)
    }

    @Synchronized
    private fun add(bluetoothDevice: BluetoothDevice): LinkController? {
        val gattConnection = fitbitGatt.getConnection(bluetoothDevice)
        return gattConnection?.let {
            val linkController = LinkController(
                it,
                linkConfigurationServiceEventListener.getDataObservable(bluetoothDevice)
            )
            linkControllersMap[bluetoothDevice] = linkController
            linkController
        }
    }

    @Synchronized
    private fun add(gattConnection: GattConnection): LinkController {
        val bluetoothDevice = gattConnection.device.btDevice
        val linkController = LinkController(
            gattConnection,
            linkConfigurationServiceEventListener.getDataObservable(bluetoothDevice)
        )
        linkControllersMap[bluetoothDevice] = linkController
        return linkController
    }

    /**
     * Registers the new service and its listeners
     * This should be called by the connection manager only when bitgatt is started
     */
    fun addLinkConfigurationService(): Completable {
        return registerListeners().andThen(gattServer.addServices(linkConfigurationService))
    }

    /**
     * This registers Connection Event listener for the Link Configuration service to bitgatt.
     * These events
     */
    private fun registerListeners(): Completable {
        return Completable.fromCallable {
            fitbitGatt.server.registerConnectionEventListener(linkConfigurationServiceEventListener)
        }
    }


}
