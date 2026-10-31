package dz.alnourelite.paymonitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureConfig {
 private const val ALIAS="ane_pay_gateway_key"
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(ALIAS,null) as? SecretKey)?.let { return it }
  val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  gen.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  return gen.generateKey()
 }
 fun save(ctx:Context,host:String,token:String){
  val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  val data=c.iv+c.doFinal(token.toByteArray(Charsets.UTF_8))
  ctx.getSharedPreferences("gateway",Context.MODE_PRIVATE).edit().putString("host",host.trimEnd('/'))
   .putString("secret",Base64.encodeToString(data,Base64.NO_WRAP)).apply()
 }
 fun host(ctx:Context)=ctx.getSharedPreferences("gateway",Context.MODE_PRIVATE).getString("host",null)
 fun token(ctx:Context):String? {
  val saved=ctx.getSharedPreferences("gateway",Context.MODE_PRIVATE).getString("secret",null)?:return null
  return try {val bytes=Base64.decode(saved,Base64.DEFAULT);val c=Cipher.getInstance("AES/GCM/NoPadding")
   c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   String(c.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)}catch(e:Exception){null}
 }
}
