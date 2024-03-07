package com.github.tvbox.osc.util

import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.ui.activity.FastSearchActivity.Companion.setCheckedSourcesForSearch
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.orhanobut.hawk.Hawk
import java.util.Arrays

object SearchHelper {
    @JvmStatic
    val sourcesForSearch: HashMap<String, String>
        get() {
            var mCheckSources = HashMap<String, String>()
            try {
                val api = Hawk.get(HawkConfig.API_URL, "")
                if (api.isEmpty()) return mCheckSources
                val mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, HashMap<String, HashMap<String, String>>())
                mCheckSources = mCheckSourcesForApi[api]?:mCheckSources
            } catch (e: Exception) {
                return mCheckSources
            }
            if (mCheckSources.isEmpty()) mCheckSources = sources
            return mCheckSources
        }

    @JvmStatic
    fun putCheckedSources(mCheckSources: HashMap<String, String>, isAll: Boolean) {
        val api = Hawk.get(HawkConfig.API_URL, "")
        if (api.isEmpty()) {
            return
        }
        var mCheckSourcesForApi = Hawk.get<HashMap<String, HashMap<String, String>>>(HawkConfig.SOURCES_FOR_SEARCH, null)
        if (isAll) {
            if (mCheckSourcesForApi == null) return
            if (mCheckSourcesForApi.containsKey(api)) mCheckSourcesForApi.remove(api)
        } else {
            if (mCheckSourcesForApi == null) mCheckSourcesForApi = HashMap()
            mCheckSourcesForApi[api] = mCheckSources
        }
        SearchActivity.setCheckedSourcesForSearch(mCheckSources)
        setCheckedSourcesForSearch(mCheckSources)
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi)
    }

    private val sources: HashMap<String, String>
        get() {
            val mCheckSources = HashMap<String, String>()
            for (bean in ApiConfig.get().sourceBeanList) {
                if (!bean.isSearchable) {
                    continue
                }
                mCheckSources[bean.key] = "1"
            }
            return mCheckSources
        }

    @JvmStatic
    fun splitWords(text: String): List<String> {
        val result: MutableList<String> = ArrayList()
        result.add(text)
        val parts = text.split("\\W+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size > 1) {
            result.addAll(Arrays.asList(*parts))
        }
        return result
    }
}
