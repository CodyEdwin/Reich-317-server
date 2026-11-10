package com.reich.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Small helper that collects the local network addresses of the device so the
 * user knows where to connect the game client to.
 */
public final class NetworkUtils {

    private NetworkUtils() {
    }

    /**
     * @return every IPv4 address of every up and running network interface.
     */
    public static List<String> getLocalAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addressesOfInterface = network.getInetAddresses();
                while (addressesOfInterface.hasMoreElements()) {
                    InetAddress address = addressesOfInterface.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Enumerating the interfaces is not allowed on every device.
        }
        Collections.sort(addresses);
        return addresses;
    }

    /**
     * @return {@code true} if the device is connected to a wifi network.
     */
    @SuppressWarnings("deprecation")
    public static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            NetworkInfo info = manager.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                return true;
            }
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wifi != null && wifi.isWifiEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * @return a human readable description of the current network type.
     */
    public static String getConnectionType(Context context) {
        if (isWifiConnected(context)) {
            return "Wi-Fi";
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? "Mobile / Ethernet" : "Network";
    }
}
