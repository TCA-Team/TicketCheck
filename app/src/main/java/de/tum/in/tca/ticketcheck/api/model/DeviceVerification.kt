package de.tum.`in`.tca.ticketcheck.api.app.model

import android.content.Context
import de.tum.`in`.tca.ticketcheck.api.AuthenticationManager
import de.tum.`in`.tca.ticketcheck.api.exception.NoPrivateKey
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

data class DeviceVerification(var signature: String = "",
                              var date: String = "",
                              var rand: String = "",
                              var device: String = "") {
    companion object {
        @Throws(NoPrivateKey::class)
        fun getDeviceVerification(c: Context): DeviceVerification {
            //Create some data
            val date = Date().toString()
            val rand = BigInteger(130, SecureRandom()).toString(32)
            val device = AuthenticationManager.getDeviceID(c)

            return DeviceVerification(date = date, rand = rand, device = device)
                    //Sign this data for verification
                    //signature = AuthenticationManager(c).sign(date + rand + device))
        }
    }
}
