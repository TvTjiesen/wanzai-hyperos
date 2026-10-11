package com.muyao.wanzaihyperos.net

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okio.ByteString.Companion.toByteString

/**
 * 城院 CAS 的密码加密 —— **照抄学校登录页自己的实现**。
 *
 * 来源（2026-10-08 直接取 `http://ca.hzcu.edu.cn/cas/login?service=…` 页面内联 JS，逐字）：
 * ```js
 * var aseKey  = CryptoJS.enc.Utf8.parse("c6dda3852e2d4be2");
 * var aes_pwd = CryptoJS.AES.encrypt(CryptoJS.enc.Utf8.parse(psw), aseKey,
 *     {mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7}).toString();
 * ```
 * 提交给表单 `password` 字段的就是 `aes_pwd`（CryptoJS Ciphertext 的 `toString()` = Base64）。
 *
 * ### 三个容易搞错的地方（Flutter 蓝本做过对照实验，这里逐条对齐）
 *
 * 1. **key 是「已 parse 成字节」的 16 字节，不是字符串。**
 *    CryptoJS 拿到字符串 key 会当成 passphrase，改走 `EVP_BytesToKey` + CBC，
 *    结果完全不同。同一个明文 `Passw0rd!`：
 *    - 字节 key（浏览器里跑的这条）→ `NDujZ9CcEZlFoAr42lwuAA==`
 *    - 字符串 key → `U2FsdGVkX1+…`（`Salted__` 前缀，一眼能认出来）
 *    16 字节 key = **AES-128**，不是 256。
 * 2. **`pad.Pkcs7` 就是 RFC 5652 的 PKCS#7**：长度已经块对齐时**仍要再补整一块**（16 个 0x10）。
 *    少补这一块，16 字节整数倍的密码就会对不上。
 *    JVM 上 `AES/ECB/PKCS5Padding` 对 16 字节块而言**就是 PKCS#7**
 *    （PKCS#5 名义上只定义 8 字节块，但各家实现对 AES 一律按块长补齐），所以直接用这个名字。
 * 3. **ECB 不需要 IV**，所以 `init` 只给 key，不要顺手塞 `IvParameterSpec`。
 *
 * 本文件不含任何用户凭据：这个 key 是学校前端公开代码里的常量
 * （浏览器里人人可见，它的作用只是「不把密码明文写在 URL/表单里」，
 * **不是**抗离线破解手段）。明文密码只作为参数穿过，绝不落盘、绝不进日志。
 */
object HzcuCasCrypto {

    /** 学校前端公开的固定密钥（16 字节 = AES-128）。 */
    private const val KEY_LITERAL = "c6dda3852e2d4be2"

    private val keyBytes = KEY_LITERAL.toByteArray(Charsets.UTF_8)

    /**
     * 提交给 CAS 表单 `password` 字段的值：
     * `Base64( AES-128-ECB-PKCS7( UTF-8(密码) ) )`。
     *
     * 空密码在这里就拦掉：空输入在补位实现里会算出负的块偏移并抛一个看不懂的异常，
     * 而「密码为空」本来就是个明确的调用方错误。
     */
    fun encryptPassword(plainPassword: String): String {
        require(plainPassword.isNotEmpty()) { "密码为空，不予加密提交" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, ALGORITHM))
        val cipherText = cipher.doFinal(plainPassword.toByteArray(Charsets.UTF_8))
        return cipherText.toByteString().base64()
    }

    /**
     * 密文长度只取决于 UTF-8 字节数（中文密码字节更长）。
     * 单独暴露是为了让「按 UTF-8 而不是 UTF-16/latin1 编码」可被测试断言。
     */
    fun utf8Length(plainPassword: String): Int =
        plainPassword.toByteArray(Charsets.UTF_8).size

    // 常量不藏在函数体里：仪器化测试与文档都要引用它们。
    const val ALGORITHM = "AES"
    const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

    /**
     * 蓝本的对照实验给出的已知向量：同一个明文用**字节 key**加密的结果。
     * 单测直接拿它对答案；对不上就说明 key 被当成 passphrase 走了 CBC。
     */
    const val KNOWN_ANSWER_PLAIN = "Passw0rd!"
    const val KNOWN_ANSWER_CIPHER = "NDujZ9CcEZlFoAr42lwuAA=="
}
