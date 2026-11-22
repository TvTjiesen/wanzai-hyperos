package com.muyao.wanzaihyperos.session

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.muyao.wanzaihyperos.net.HzcuException
import java.security.KeyStore
import java.util.IdentityHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭据存储的抽象。**全项目唯一碰门户/TronClass 凭据的地方。**
 *
 * 红线（计划 §12.1）：
 * - 密码、ACCESS_TOKEN、tempcode、CAS 会话 Cookie **只允许**存在于内存和这里；
 * - 禁止写入普通 SharedPreferences（明文）、文件、日志、git、任何输出；
 * - 本包所有日志出口只允许输出「有没有 / 多长」，**绝不输出值本身**。
 *
 * 做成接口是为了让单测能用内存实现跑完全部会话逻辑，
 * 而「真加密」这件事由 [EncryptedSecureStore] 单独负责、单独验证。
 */
interface SecureStore {

    fun read(key: String): String?

    /** [value] 为 null 表示删除该键。 */
    fun write(key: String, value: String?)

    fun delete(key: String)

    /**
     * 同批持久化全部变更，null 表示删除；只有确认落盘后才返回。
     * 不支持事务的实现必须拒绝多键操作，不能用逐键 write 冒充原子提交。
     */
    fun writeAtomically(changes: Map<String, String?>) {
        when (changes.size) {
            0 -> return
            1 -> changes.entries.single().let { (key, value) -> write(key, value) }
            else -> throw HzcuException.Storage("writeAtomically", "AtomicWriteUnsupported")
        }
    }
}

/**
 * 真加密实现：AES-256-GCM，主密钥由 **Android Keystore** 持有且不可导出。
 *
 * ### 为什么不用 `androidx.security:security-crypto`
 * 那个库（`EncryptedSharedPreferences` / `MasterKey`）**整个公开 API 已被 AndroidX
 * 标记为 deprecated**，且没有官方的替代品。在「凭据永不落盘」这条红线的路径上
 * 依赖一个不再维护的库，不如自己用平台原语实现 —— 这个模式本身就几十行，
 * 而且我们因此完全掌控错误处理（见下面「解不开怎么办」）。
 *
 * ### 存储格式（**改动它等于让所有用户的凭据失效，务必谨慎**）
 * 每个键对应的值是一段 Base64（NO_WRAP），解码后为：
 * ```
 * [0]      格式版本，当前为 1
 * [1..12]  GCM IV，12 字节（每次加密都由系统重新随机生成）
 * [13..]   GCM 密文 + 16 字节认证标签
 * ```
 * 版本字节是给将来的格式迁移留的：换算法时能识别出旧数据并**明确降级成未登录**，
 * 而不是拿新代码去解旧密文然后抛一个看不懂的异常。
 *
 * ### 为什么键名是明文
 * 我们不加密键名。键名是固定常量（`hzcu_access_token` 之类），**不是秘密**；
 * 秘密只在值里。EncryptedSharedPreferences 连键名一起加密，代价是启动时要
 * 多解一遍全部键，对这个场景没有收益。
 *
 * ### 解不开怎么办（重要）
 * Keystore 主密钥**不随备份走**。刷机、恢复出厂、换机、部分 root ROM 上，
 * 密文还在但密钥没了 → 解密抛 `AEADBadTagException` / `KeyStoreException`。
 * 这时我们：
 * 1. **删掉这一条**密文（它已经永远解不开了，留着只会每次启动都抛）；
 * 2. 抛 [HzcuException.Storage]（只带 op + 异常类名，不带消息）；
 * 3. 由 [StoredSession] 把登录态降级成 [SessionState.Unknown]。
 * 最坏结果是「要重新登录一次」，而不是把首页拖崩。
 */
class EncryptedSecureStore internal constructor(
    private val prefs: SharedPreferences,
    private val encrypt: (String) -> String,
    private val decrypt: (String) -> String,
) : SecureStore {

    private val state = synchronized(states) { states.getOrPut(prefs) { PersistenceState() } }
    private val lock = state.lock
    private val uncertainKeys = state.uncertainKeys

    override fun read(key: String): String? = synchronized(lock) {
        checkReadable(key)
        val stored = storageOperation("read") { prefs.getString(key, null) } ?: return@synchronized null
        try {
            decrypt(stored)
        } catch (error: Exception) {
            val failure = HzcuException.Storage("read", error.javaClass.simpleName)
            try {
                commitChanges(mapOf(key to null), "delete")
            } catch (cleanup: HzcuException.Storage) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    override fun write(key: String, value: String?) = synchronized(lock) {
        commitChanges(mapOf(key to value), if (value == null) "delete" else "write")
    }

    override fun delete(key: String) = synchronized(lock) {
        commitChanges(mapOf(key to null), "delete")
    }

    override fun writeAtomically(changes: Map<String, String?>) = synchronized(lock) {
        commitChanges(changes, "writeAtomically")
    }

    private fun checkReadable(key: String) {
        // SharedPreferences 在 commit 失败时也可能更新进程缓存，不能把它当作已落盘的数据。
        if (key in uncertainKeys) throw HzcuException.Storage("read", "CommitOutcomeUnknown")
    }

    private fun commitChanges(changes: Map<String, String?>, op: String) {
        if (changes.isEmpty()) return
        if (uncertainKeys.isNotEmpty()) {
            // 同值重试可能跳过磁盘；写其它源也会把失败缓存一起落盘。真实进程重启前禁止再写。
            throw HzcuException.Storage(op, "CommitOutcomeUnknown")
        }
        // 在创建 Editor 前加密全部值：第 N 个加密失败时，前面的键也没有被写入。
        val encoded = storageOperation(op) {
            changes.mapValues { (_, value) -> value?.let(encrypt) }
        }
        try {
            storageOperation(op) {
                val editor = prefs.edit()
                for ((key, value) in encoded) {
                    if (value == null) editor.remove(key) else editor.putString(key, value)
                }
                if (!editor.commit()) throw HzcuException.Storage(op, "CommitFailed")
            }
        } catch (error: HzcuException.Storage) {
            uncertainKeys.addAll(encoded.keys)
            throw error
        }
    }

    private inline fun <T> storageOperation(op: String, action: () -> T): T = try {
        action()
    } catch (error: HzcuException.Storage) {
        throw error
    } catch (error: Exception) {
        throw HzcuException.Storage(op, error.javaClass.simpleName)
    }

    private class PersistenceState {
        val lock = Any()
        val uncertainKeys = mutableSetOf<String>()
    }

    companion object {
        // Context 缓存同名 SharedPreferences，重开 wrapper 不能绕过进程缓存的不确定状态。
        private val states = IdentityHashMap<SharedPreferences, PersistenceState>()

        /**
         * 城院凭据专用文件名。
         *
         * 与 Flutter 蓝本的 `WanzaiHzcuSecureStorage` **同名**，这是有意的：
         * 两个平台各自的存储后端是独立的（那边是 flutter_secure_storage 的文件，
         * 这边是我们自己的），不会互相覆盖；但「同一个用途就叫同一个名字」
         * 让排查时不会认错库。
         *
         * ★ 绝不要改成别的库（例如 Celechron 遗留的）在用的文件名：
         * 那会把别人已有的键一起读进来，属于「乱改业主的东西」。
         */
        const val PREFS_FILE_NAME = "WanzaiHzcuSecureStorage"

        /** Keystore 里的主密钥别名。**不要改**，改了等于让所有凭据失效。 */
        const val MASTER_KEY_ALIAS = "wanzai_hzcu_master_key"

        /**
         * 打开（必要时创建）加密存储。
         *
         * **必须在后台线程首次调用**：首次会生成 Keystore 主密钥，
         * 在低端机上可能耗时上百毫秒。App 启动时用一个协程预热即可。
         */
        fun open(context: Context): SecureStore {
            val crypto = KeystoreCrypto(MASTER_KEY_ALIAS)
            return EncryptedSecureStore(
                prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE),
                encrypt = crypto::encrypt,
                decrypt = crypto::decrypt,
            )
        }
    }
}

/**
 * Android Keystore 的 AES-GCM 加解密。
 *
 * 单独一个类是为了让「加密算法与格式」这件事只有一处实现，
 * 也让仪器化测试可以直接对它做往返与篡改检测。
 */
class KeystoreCrypto(private val keyAlias: String = EncryptedSecureStore.MASTER_KEY_ALIAS) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** GCM 推荐的 IV 长度。**必须与 [decrypt] 读取的长度一致。** */
        const val IV_LENGTH = 12

        /** 认证标签长度（位）。128 是 GCM 的标准值。 */
        private const val TAG_LENGTH_BITS = 128

        /** 存储格式版本。换算法/换布局时 +1，并在这里保留旧版本的读取分支。 */
        const val FORMAT_VERSION: Byte = 1
    }

    /**
     * 取（必要时生成）主密钥。
     *
     * `setRandomizedEncryptionRequired(true)` 保证**每次加密都由系统生成新的随机 IV**
     * —— 这正是 GCM 的生命线：同一个密钥下重复使用 IV 会直接泄漏明文。
     * 打开这个开关后，如果代码试图用自带 IV 加密，系统会抛异常，等于多一道保险。
     */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                // 刻意**不**要求用户认证：App 需要在后台读凭据刷新角标，
                // 要求每次解锁会让后台刷新全部失败。
                .build(),
        )
        return generator.generateKey()
    }

    /** 明文 → Base64（版本 + IV + 密文）。 */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "GCM IV 长度异常：${iv.size}" }
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(1 + IV_LENGTH + cipherText.size)
        packed[0] = FORMAT_VERSION
        System.arraycopy(iv, 0, packed, 1, IV_LENGTH)
        System.arraycopy(cipherText, 0, packed, 1 + IV_LENGTH, cipherText.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** Base64 → 明文。格式不对、被篡改、或密钥换了都会抛异常。 */
    fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 1 + IV_LENGTH) { "密文长度不足：${packed.size}" }
        val version = packed[0]
        require(version == FORMAT_VERSION) { "未知的存储格式版本：$version" }

        val iv = packed.copyOfRange(1, 1 + IV_LENGTH)
        val cipherText = packed.copyOfRange(1 + IV_LENGTH, packed.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    /** 测试用：删掉主密钥，模拟「刷机后密文还在但密钥没了」。 */
    fun deleteKeyForTesting() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
    }
}

/**
 * 内存实现。**只允许用于单测与 Compose 预览。**
 *
 * 生产代码出现它 = 凭据明文留在内存里且不加密落盘，review 时应直接否掉。
 */
class InMemorySecureStore(
    initial: Map<String, String> = emptyMap(),
) : SecureStore {

    private var values = LinkedHashMap<String, String>(initial)
    private val lock = Any()

    override fun read(key: String): String? = synchronized(lock) { values[key] }

    override fun write(key: String, value: String?) = writeAtomically(mapOf(key to value))

    override fun delete(key: String) = writeAtomically(mapOf(key to null))

    override fun writeAtomically(changes: Map<String, String?>) = synchronized(lock) {
        val next = LinkedHashMap(values)
        for ((key, value) in changes) {
            if (value == null) next.remove(key) else next[key] = value
        }
        values = next
    }

    /** 测试用：看看到底往「存储」里写了哪些键（**不是值**）。 */
    fun keys(): Set<String> = synchronized(lock) { values.keys.toSet() }
}

/**
 * 会按需失败的存储替身，用来验证「存储坏了必须降级成未知、而不是判成未登录」。
 */
class FailingSecureStore(private val errorCode: String = "TestKeyStoreFailure") : SecureStore {

    override fun read(key: String): String? = throw HzcuException.Storage("read", errorCode)

    override fun write(key: String, value: String?) =
        throw HzcuException.Storage("write", errorCode)

    override fun delete(key: String) = throw HzcuException.Storage("delete", errorCode)

    override fun writeAtomically(changes: Map<String, String?>) =
        throw HzcuException.Storage("writeAtomically", errorCode)
}
