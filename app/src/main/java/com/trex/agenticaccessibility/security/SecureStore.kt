package com.trex.agenticaccessibility.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs=context.getSharedPreferences("secure_store",Context.MODE_PRIVATE)
    private val alias="agentic_store_key"
    init { if (!java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias)) { val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore"); g.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build()); g.generateKey() } }
    private fun key():SecretKey { val ks=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}; return (ks.getEntry(alias,null) as java.security.KeyStore.SecretKeyEntry).secretKey }
    fun put(name:String,value:String){ val iv=ByteArray(12);java.security.SecureRandom().nextBytes(iv);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),GCMParameterSpec(128,iv));val ct=c.doFinal(value.toByteArray(StandardCharsets.UTF_8));prefs.edit().putString(name,Base64.encodeToString(iv+ct,Base64.NO_WRAP)).apply() }
    fun get(name:String):String?=runCatching{val b=Base64.decode(prefs.getString(name,null)?:return null,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)));String(c.doFinal(b.copyOfRange(12,b.size)),StandardCharsets.UTF_8)}.getOrNull()
    fun clear(){prefs.edit().clear().apply()}
}
