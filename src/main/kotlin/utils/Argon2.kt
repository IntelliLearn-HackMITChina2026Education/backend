package net.sfls.lh.intellilearn.utils

import de.mkammerer.argon2.Argon2Factory

object Argon2idUtil {

    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    /**
     * 加密密码
     * @param password 明文密码
     * @param iterations 迭代次数
     * @param memory 内存占用 KB
     * @param parallelism 并行度
     * @return Argon2id 哈希字符串（含盐值，可直接存储）
     */
    fun hash(
        password: String,
        iterations: Int = 3,
        memory: Int = 65536,
        parallelism: Int = 1
    ): String = argon2.hash(iterations, memory, parallelism, password.toCharArray())

    /**
     * 验证密码
     * @param hash 存储的哈希字符串
     * @param password 待验证的明文密码
     * @return 验证通过返回 true，否则 false
     */
    fun verify(hash: String, password: String) = argon2.verify(hash, password.toCharArray())

    /**
     * 验证密码，并判断是否需要重新哈希（参数已变更时）
     */
    fun verifyAndRehashIfNeeded(
        hash: String,
        password: String,
        iterations: Int = 3,
        memory: Int = 65536,
        parallelism: Int = 1
    ): Pair<Boolean, String?> {
        val valid = argon2.verify(hash, password.toCharArray())
        if (!valid) return Pair(false, null)

        val needsRehash = argon2.needsRehash(hash, iterations, memory, parallelism)
        val newHash = if (needsRehash) hash(password, iterations, memory, parallelism) else null
        return Pair(true, newHash)
    }
}
