package com.focal.android.transport

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS for optional FOCL transport on [DEFAULT_TLS_PORT]. Uses the embedded LAN keystore
 * (generated via `scripts/generate_lan_keystore.sh`). Pairing PIN is still required after TLS connect.
 */
object FocalLanTls {

    const val DEFAULT_TLS_PORT = 8443
    private const val KEYSTORE_ASSET = "focal_lan.jks"
    private const val KEYSTORE_PASSWORD = "focallan"
    private const val KEY_ALIAS = "focal"

    data class TlsMaterial(
        val sslContext: SSLContext,
        val certificateFingerprintSha256Base64: String
    )

    fun loadServerMaterial(context: Context): TlsMaterial {
        val keyStore = KeyStore.getInstance("JKS")
        context.assets.open(KEYSTORE_ASSET).use { stream ->
            keyStore.load(stream, KEYSTORE_PASSWORD.toCharArray())
        }
        val cert: Certificate = keyStore.getCertificate(KEY_ALIAS)
        val fingerprint = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(cert.encoded),
            Base64.NO_WRAP
        )

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray())
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, null)
        return TlsMaterial(sslContext, fingerprint)
    }

    fun createTrustAllClientContext(): SSLContext {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
        )
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, null)
        return ctx
    }

    fun serverSocketFactory(material: TlsMaterial): SSLServerSocketFactory =
        material.sslContext.serverSocketFactory

    fun clientSocketFactory(): SSLSocketFactory =
        createTrustAllClientContext().socketFactory
}
