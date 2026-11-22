package com.karaokelink.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [WifiRoutingTest.FakeConnectivity::class, NsdDiscoveryTest.FakeNsd::class])
class WifiRoutingTest {
    @Implements(ConnectivityManager::class)
    class FakeConnectivity {
        companion object { val networks = linkedMapOf<Network, NetworkCapabilities>() }
        @Implementation fun getAllNetworks(): Array<Network> = networks.keys.toTypedArray()
        @Implementation fun getNetworkCapabilities(network: Network): NetworkCapabilities? = networks[network]
    }
    @Before fun setup() { FakeConnectivity.networks.clear() }
    private fun capabilities(vararg transports: Int): NetworkCapabilities {
        val result = ShadowNetworkCapabilities.newInstance()
        transports.forEach { shadowOf(result).addTransportType(it) }
        shadowOf(result).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return result
    }
    @Test fun microphoneUsesWifiWithoutInternetInsteadOfCellular() {
        val cell = ShadowNetwork.newInstance(11); val wifi = ShadowNetwork.newInstance(12)
        FakeConnectivity.networks[cell] = capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)
        FakeConnectivity.networks[wifi] = capabilities(NetworkCapabilities.TRANSPORT_WIFI)
        val transport = LanTransport(nsdTestContext()) { emptySet() }
        try {
            transport.start(false)
            assertEquals(2, shadowOf(wifi).boundSocketCount())
            assertEquals(0, shadowOf(cell).boundSocketCount())
            assertEquals(wifi.networkHandle, transport.networkHandle)
        } finally { transport.stop() }
        assertEquals(0L, transport.networkHandle)
    }
    @Test fun playerNeverBindsItsListeningSocketsToTheUpstreamWifi() {
        val wifi = ShadowNetwork.newInstance(12)
        FakeConnectivity.networks[wifi] = capabilities(NetworkCapabilities.TRANSPORT_WIFI)
        val transport = LanTransport(nsdTestContext()) { emptySet() }
        try {
            transport.start(true)
            assertEquals(0, shadowOf(wifi).boundSocketCount()); assertEquals(0L, transport.networkHandle)
        } finally { transport.stop() }
    }
    @Test fun vpnWifiIsNotSelectedAsDirectMicrophoneNetwork() {
        val vpn = ShadowNetwork.newInstance(13)
        FakeConnectivity.networks[vpn] = capabilities(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_VPN)
        val transport = LanTransport(nsdTestContext()) { emptySet() }
        try {
            transport.start(false)
            assertEquals(0, shadowOf(vpn).boundSocketCount()); assertEquals(0L, transport.networkHandle)
        } finally { transport.stop() }
    }
}
