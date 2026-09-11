
import android.net.Uri
import androidx.navigation.NavBackStackEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object NavArgsParser {
    val gson = Gson()

    /**
     * 解析任意类型的导航参数
     * @param key 参数键名（如 "tradePair"）
     * @param typeToken 用于保留泛型类型（如 `object : TypeToken<TradePairModel>() {}`）
     */
    inline fun <reified T> parse(
        entry: NavBackStackEntry,
        key: String,
        typeToken: TypeToken<T>? = null
    ): T? {

        return try {
            val json = entry.arguments?.getString(key) ?: return null
            val type = typeToken?.type ?: T::class.java
            gson.fromJson<T>(Uri.decode(json), type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成安全的导航路由字符串
     * @param key 参数键名
     * @param value 要传递的对象
     */
    fun <T> createRouteParam(key: String, value: T): String {
        return Uri.encode(gson.toJson(value))
    }

}