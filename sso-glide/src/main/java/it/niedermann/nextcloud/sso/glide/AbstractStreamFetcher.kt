package it.niedermann.nextcloud.sso.glide

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.api.NextcloudAPI.ApiConnectedListener
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches an [InputStream] using the Nextcloud SSO library.
 */
abstract class AbstractStreamFetcher<T>(
    private val context: Context,
    private val model: T,
    private val apiFactory: ApiFactory = object : ApiFactory {
        override fun build(
            context: Context,
            ssoAccount: SingleSignOnAccount,
            gson: Gson,
            callback: ApiConnectedListener
        ): NextcloudAPI {
            return NextcloudAPI(context, ssoAccount, gson, callback)
        }
    }
) : DataFetcher<InputStream> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream?>) {
        try {
            val ssoAccount: SingleSignOnAccount = getSingleSignOnAccount(context, model)
            var client = INITIALIZED_APIs[ssoAccount.name]
            val didInitialize = if (client == null) {
                client = apiFactory.build(
                    context,
                    ssoAccount,
                    GsonBuilder().create(),
                    object : ApiConnectedListener {
                        override fun onConnected() {
                            Log.v(TAG, "SSO API successfully initialized")
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, ex.message, ex)
                        }
                    })
                INITIALIZED_APIs[ssoAccount.name] = client
                true
            } else {
                false
            }
            val url = getAbsoluteUrl(ssoAccount, model.toString())
            val nextcloudRequest = NextcloudRequest.Builder()
                .setMethod(METHOD_GET)
                .setUrl(url.path.substring(URL(ssoAccount.url).path.length))
                .setParameter(getQueryParams(url))
                .build()
            try {
                val response = client.performNetworkRequestV2(nextcloudRequest)
                callback.onDataReady(response.body)
            } catch (e: TokenMismatchException) {
                Log.w(
                    TAG,
                    "SSO Glide loader failed with ${TokenMismatchException::class.java.simpleName}"
                )
                resetInitializedApi(ssoAccount.name)
                if (didInitialize) {
                    Log.i(
                        TAG,
                        "This API instance failed at the very first call, so we won't try to re-initialize the API this time…"
                    )
                    callback.onLoadFailed(e)
                } else {
                    Log.i(TAG, "This API instance worked before, so we try to re-initialize it…")
                    loadData(priority, callback)
                }
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    abstract fun getSingleSignOnAccount(context: Context, model: T): SingleSignOnAccount

    private fun getAbsoluteUrl(ssoAccount: SingleSignOnAccount, model: String): URL {
        return try {
            // Absolute URL
            val url = URL(model)
            // Verify it starts with the given ssoAccount
            if (model.startsWith(ssoAccount.url)) {
                convertFileIdUrlToPreviewUrl(ssoAccount, url)
            } else {
                throw IllegalArgumentException("Given ${SingleSignOnAccount::class.java.simpleName} does not match the URL (${ssoAccount.url} vs. ${model}). Pass correct ${SingleSignOnAccount::class.java.simpleName} or use default ${GlideUrl::class.java.simpleName} (or a plain ${String::class.java.simpleName}) to try fetching with the current ${SingleSignOnAccount::class.java.simpleName} stored in ${SingleAccountHelper::class.java.simpleName}.")
            }
        } catch (e: MalformedURLException) {
            // This might be a relative URL, prepend the URL of the ssoAccount
            if (model.startsWith("/")) {
                convertFileIdUrlToPreviewUrl(ssoAccount, URL(ssoAccount.url + model))
            } else {
                throw IllegalArgumentException("URL must be absolute (starting with protocol and host or with a slash character).")
            }
        }
    }

    private fun convertFileIdUrlToPreviewUrl(ssoAccount: SingleSignOnAccount, url: URL): URL {
        val fileId = REGEX_FILE_ID.find(url.path)?.groupValues?.get(2)
        if (fileId != null) {
            return URL("${ssoAccount.url}/index.php/core/preview?fileId=${fileId}&x=${context.resources.displayMetrics.widthPixels}&y=${context.resources.displayMetrics.heightPixels}&a=true")
        }
        val shareId = REGEX_SHARE_ID.find(url.path)?.groupValues?.get(2)
        if (shareId != null) {
            return URL("${ssoAccount.url}/index.php/s/${shareId}/download")
        }
        return url
    }

    private fun getQueryParams(url: URL): Map<String?, String?> {
        if (TextUtils.isEmpty(url.query)) {
            return emptyMap<String?, String>()
        }
        val queryParams: MutableMap<String?, String?> = HashMap()
        for (param in url.query.split("&").toTypedArray()) {
            if ("c" == param) {
                Log.w(
                    TAG,
                    "Stripped query parameter \"c\". This is usually used as CSRF protection and must not be sent by the client because the SSO authenticates itself."
                )
            } else {
                val idx = param.indexOf("=")
                val key = if (idx > 0) param.substring(0, idx) else param
                val value =
                    if (idx > 0 && param.length > idx + 1) param.substring(idx + 1) else null
                if (!TextUtils.isEmpty(key)) {
                    queryParams[key] = value
                }
            }
        }
        return queryParams
    }

    override fun cleanup() {
        // Nothing to do here…
    }

    override fun cancel() {
        // Nothing to do here…
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }

    interface ApiFactory {
        fun build(
            context: Context,
            ssoAccount: SingleSignOnAccount,
            gson: Gson,
            callback: ApiConnectedListener
        ): NextcloudAPI
    }

    companion object {
        private val TAG = AbstractStreamFetcher::class.java.simpleName
        private const val METHOD_GET = "GET"
        private val INITIALIZED_APIs: MutableMap<String, NextcloudAPI> = ConcurrentHashMap()
        private val REGEX_FILE_ID = Regex("^(/index\\.php)?/f/(\\d+)(/)?$")
        private val REGEX_SHARE_ID = Regex("^(/index\\.php)?/s/(\\w+)(/|/download|/download/)?$")

        @VisibleForTesting
        fun resetInitializedApis() {
            for ((key, _) in INITIALIZED_APIs) {
                resetInitializedApi(key)
            }
        }

        private fun resetInitializedApi(key: String) {
            INITIALIZED_APIs[key]?.stop()
            INITIALIZED_APIs.remove(key)
        }
    }
}