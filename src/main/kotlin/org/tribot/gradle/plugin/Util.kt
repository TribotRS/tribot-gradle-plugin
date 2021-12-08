package org.tribot.gradle.plugin

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

internal fun getTribotDirectory(): File {
    var directory: File?
    val userHome = System.getProperty("user.home")
    val osName = System.getProperty("os.name").toLowerCase()
    if (osName.contains("win")) {
        val appData = System.getenv("APPDATA")
        if (appData == null || appData.length < 1) {
            directory = File(userHome, ".tribot" + File.separatorChar)
        } else {
            directory = File(appData, ".tribot" + File.separatorChar)
        }
    } else if (osName.contains("solaris") || osName.contains("linux") || osName.contains("sunos") ||
            osName.contains("unix")
    ) {
        directory = File(userHome, ".tribot" + File.separatorChar)
    } else if (osName.contains("mac")) {
        directory = File(
                userHome,
                "Library" + File.separatorChar + "Application Support" + File.separatorChar +
                        "tribot"
        )
    } else {
        directory = File(userHome, "tribot" + File.separatorChar)
    }

    if (!directory.exists() && !directory.mkdirs()) {
        directory = File("data")
        println(
                ("Couldn't create separate application data directory. Using application data directory" + " as: " +
                        directory.absolutePath)
        )
    }
    return directory
}

internal fun getMacAddress() : String? {
    var builder = StringBuilder()

    try {
        Formatter(builder).use { formatter ->
            val address = InetAddress.getLocalHost()
            val ni = NetworkInterface.getByInetAddress(address)

            if (ni != null) {
                val mac = ni.hardwareAddress
                if (mac != null) {
                    for (i in mac.indices) {
                        formatter.format("%02X%s", mac[i], if (i < mac.size - 1) "-" else "")
                    }
                }
                if (builder.length > 0) {
                    formatter.close()
                    return builder.toString()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    builder = StringBuilder()

    try {
        Formatter(builder).use { formatter ->
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                var str: String
                val network = interfaces.nextElement() ?: continue
                val mac = network.hardwareAddress ?: continue
                val strBuilder = StringBuilder()
                for (i in mac.indices) {
                    strBuilder.append(String.format("%02X%s", mac[i], if (i < mac.size - 1) "" else "")).append("-")
                }
                str = strBuilder.toString()
                if (str.length < 1) {
                    continue
                }
                formatter.close()
                return str.substring(0, str.length - 1)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    builder = StringBuilder()

    try {
        Formatter(builder).use { formatter ->
            val ni = NetworkInterface.getByName("localhost")
            if (ni != null) {
                val mac = ni.hardwareAddress
                if (mac != null) {
                    for (i in mac.indices) {
                        formatter.format("%02X%s", mac[i], if (i < mac.size - 1) "-" else "")
                    }
                }
                if (builder.length > 0) {
                    formatter.close()
                    return builder.toString()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    builder = StringBuilder()

    try {
        Formatter(builder).use { formatter ->
            val ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost())
            if (ni != null) {
                val mac = ni.hardwareAddress
                if (mac != null) {
                    for (i in mac.indices) {
                        formatter.format("%02X%s", mac[i], if (i < mac.size - 1) "-" else "")
                    }
                }
                if (builder.length > 0) {
                    formatter.close()
                    return builder.toString()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    builder = StringBuilder()

    try {
        Formatter(builder).use { formatter ->
            val ps = Runtime.getRuntime().exec("ifconfig -a")
            val `in` = ps.inputStream
            val reader = BufferedReader(InputStreamReader(`in`))
            var line: String
            while (reader.readLine().also { line = it } != null) {
                if (line.contains("HWaddr")) {
                    formatter.close()
                    reader.close()
                    return line.substring(line.length - 19, line.length - 2).uppercase(Locale.getDefault())
                }
            }
            reader.close()
        }
    } catch (e: IOException) {
        // e.printStackTrace();
    }

    return System.getProperty("user.name" + "*" + System.getProperty("os.name"))
}