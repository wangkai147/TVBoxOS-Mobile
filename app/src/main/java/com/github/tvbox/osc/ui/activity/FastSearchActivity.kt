package com.github.tvbox.osc.ui.activity

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.TmdbVodInfo
import com.github.tvbox.osc.databinding.ActivityFastSearchBinding
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog
import com.github.tvbox.osc.ui.dialog.SearchSuggestionsDialog
import com.github.tvbox.osc.ui.dialog.TmdbVodInfoDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.android.material.tabs.TabLayout
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.interfaces.SimpleCallback
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.orhanobut.hawk.Hawk
import com.zhy.view.flowlayout.FlowLayout
import com.zhy.view.flowlayout.TagAdapter
import okhttp3.Response
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author pj567
 * @date :2020/12/23
 */
class FastSearchActivity : BaseVbActivity<ActivityFastSearchBinding>(), TextWatcher {
    private lateinit var sourceViewModel: SourceViewModel

    private var searchAdapter: FastSearchAdapter = FastSearchAdapter()
    private var searchAdapterFilter: FastSearchAdapter = FastSearchAdapter()
    private var searchTitle: String = ""
    private val spNames = HashMap<String, String>()
    private var isFilterMode = false
    private var searchFilterKey: String = "" // 过滤的key
    private val resultVideos = HashMap<String, ArrayList<Movie.Video>>() // 搜索结果
    private val allRunCount = AtomicInteger(0)
    private var pauseRunnable: MutableList<Runnable>? = null
    private var searchExecutorService: ExecutorService = Executors.newFixedThreadPool(10)

    override fun init() {
        initView()
        initViewModel()
        initData()
        //历史搜索
        initHistorySearch()
        // 热门搜索
        hotWords
    }


    override fun onResume() {
        super.onResume()
        if (pauseRunnable?.isNotEmpty() == true) {
            allRunCount.set(pauseRunnable!!.size)
            for (runnable in pauseRunnable!!) {
                if (searchExecutorService.isShutdown) {
                    searchExecutorService = Executors.newFixedThreadPool(10)
                }
                searchExecutorService.execute(runnable)
            }
            pauseRunnable?.clear()
            pauseRunnable = null
        }
    }

    private fun initView() {
        mBinding.etSearch.setOnEditorActionListener { _: TextView, actionId: Int, _: KeyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(mBinding.etSearch.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }

        mBinding.etSearch.addTextChangedListener(this)
        mBinding.ivFilter.setOnClickListener { filterSearchSource() }
        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.ivSearch.setOnClickListener { search(mBinding.etSearch.text.toString()) }

        mBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filterResult(tab.text.toString())
            }

            override fun onTabUnselected(p0: TabLayout.Tab) {
            }

            override fun onTabReselected(p0: TabLayout.Tab) {
            }

        })


        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.layoutManager = LinearLayoutManager(this.mContext)

        mBinding.mGridView.adapter = searchAdapter

        searchAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
            FastClickCheckUtil.check(view)
            val video = searchAdapter.data[position]
            if (video != null) {
                try {
                    pauseRunnable = searchExecutorService.shutdownNow()
                    JsLoader.stopAll()
                } catch (ignored: Throwable) {
                }
                val bundle = Bundle()
                bundle.putString("id", video.id)
                bundle.putString("sourceKey", video.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }
        }


        mBinding.mGridViewFilter.layoutManager = LinearLayoutManager(mContext)
        mBinding.mGridViewFilter.adapter = searchAdapterFilter
        searchAdapterFilter.setOnItemClickListener { _: BaseQuickAdapter<*, *>, view: View, position: Int ->
            FastClickCheckUtil.check(view)
            val video = searchAdapterFilter.data[position]
            if (video != null) {
                try {
                    pauseRunnable = searchExecutorService.shutdownNow()
                    JsLoader.stopAll()
                } catch (ignored: Throwable) {
                }
                val bundle = Bundle()
                bundle.putString("id", video.id)
                bundle.putString("sourceKey", video.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }
        }

        searchAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>, _: View, position: Int ->
                val video = searchAdapter.data[position]
                if (!TextUtils.isEmpty(video.name)) {
                    queryFromTMDB(video.name)
                }
                true
            }
        searchAdapterFilter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, _: View, position: Int ->
                val video = searchAdapterFilter.data[position]
                if (!TextUtils.isEmpty(video.name)) {
                    queryFromTMDB(video.name)
                }
                true
            }
        setLoadSir(mBinding.llLayout)
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this)[SourceViewModel::class.java]
    }

    /**
     * 指定搜索源(过滤) 可以避免重复创建对话框
     */
    private var mSearchCheckboxDialog: SearchCheckboxDialog? = null
    private fun filterSearchSource() {
        if (mSearchCheckboxDialog == null) {
            val allSourceBean = ApiConfig.get().sourceBeanList
            val searchAbleSource = allSourceBean.filter { it.isSearchable }.toMutableList()
            mSearchCheckboxDialog =
                SearchCheckboxDialog(this@FastSearchActivity, searchAbleSource, mCheckSources)
        }
        mSearchCheckboxDialog?.setOnDismissListener { dialog: DialogInterface -> dialog.dismiss() }
        mSearchCheckboxDialog?.show()
    }

    private fun filterResult(spName: String) {
        if (spName === "全部显示") {
            mBinding.mGridView.visibility = View.VISIBLE
            mBinding.mGridViewFilter.visibility = View.GONE
            return
        }
        mBinding.mGridView.visibility = View.GONE
        mBinding.mGridViewFilter.visibility = View.VISIBLE
        val key = spNames[spName] ?: ""
        if (key.isEmpty()) return
        if (searchFilterKey === key) return
        searchFilterKey = key

        val list: ArrayList<Movie.Video>? = resultVideos[key]
        searchAdapterFilter.setNewData(list)
    }

    private fun initData() {
        initCheckedSourcesForSearch()
        val intent = intent
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            if (title != null) {
                showLoading()
                search(title)
            }
        }
    }


    /**
     * @param isHide 是否隐藏搜索词
     */
    private fun hideHotAndHistorySearch(isHide: Boolean) = if (isHide) {
        mBinding.llSearchSuggest.visibility = View.GONE
        mBinding.llSearchResult.visibility = View.VISIBLE
    } else {
        mBinding.llSearchSuggest.visibility = View.VISIBLE
        mBinding.llSearchResult.visibility = View.GONE
    }

    private fun initHistorySearch() {
        val mSearchHistory: List<String> = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList())

        mBinding.llHistory.visibility = if (mSearchHistory.isNotEmpty()) View.VISIBLE else View.GONE
        mBinding.flHistory.adapter = object : TagAdapter<String?>(mSearchHistory) {
            override fun getView(parent: FlowLayout, position: Int, s: String?): View {
                val tv = LayoutInflater.from(this@FastSearchActivity).inflate(
                    R.layout.item_search_word_hot, mBinding.flHistory, false
                ) as TextView
                tv.text = s
                return tv
            }
        }

        mBinding.flHistory.setOnTagClickListener { _: View, position: Int, _: FlowLayout ->
            search(
                mSearchHistory[position]
            )
            true
        }

        findViewById<View>(R.id.iv_clear_history).setOnClickListener { view: View ->
            Hawk.put(HawkConfig.HISTORY_SEARCH, ArrayList<Any>())
            //FlowLayout及其adapter貌似没有清空数据的api,简单粗暴重置
            view.postDelayed({ this.initHistorySearch() }, 300)
        }
    }

    private val hotWords: Unit
        /**
         * 热门搜索
         */
        get() {
            // 加载热词
            OkGo.get<String>("https://node.video.qq.com/x/api/hot_search") //        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
                //                .params("cat", "1")
                .params("channdlId", "0").params("_", System.currentTimeMillis())
                .execute(object : AbsCallback<String>() {
                    override fun onSuccess(response: com.lzy.okgo.model.Response<String>) {
                        try {
                            val hots = ArrayList<String>()
                            val itemList =
                                JsonParser.parseString(response.body()).asJsonObject["data"].asJsonObject["mapResult"].asJsonObject["0"].asJsonObject["listInfo"].asJsonArray
                            //                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (ele in itemList) {
                                val obj = ele as JsonObject
                                hots.add(obj["title"].asString.trim { it <= ' ' }
                                    .replace("[<>《》\\-]".toRegex(), "").split(" ".toRegex())
                                    .dropLastWhile { it.isEmpty() }.toTypedArray()[0])
                            }
                            mBinding.flHot.adapter = object : TagAdapter<String>(hots) {
                                override fun getView(
                                    parent: FlowLayout, position: Int, s: String?
                                ): View {
                                    val tv = LayoutInflater.from(this@FastSearchActivity).inflate(
                                        R.layout.item_search_word_hot, mBinding.flHot, false
                                    ) as TextView
                                    tv.text = s
                                    return tv
                                }
                            }

                            mBinding.flHot.setOnTagClickListener { _: View, position: Int, _: FlowLayout ->
                                search(
                                    hots[position]
                                )
                                true
                            }
                        } catch (ignored: Throwable) {
                        }
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: Response): String {
                        return response.body()?.string() ?: ""
                    }
                })
        }


    /**
     * 联想搜索
     */
    private fun getSuggest(text: String) {
        // 加载热词
        OkGo.get<String>("https://suggest.video.iqiyi.com/?if=mobile&key=$text")
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String>) {
                    val titles: MutableList<String> = ArrayList()
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val dataArray = json["data"].asJsonArray
                        for (data in dataArray) {
                            val item = data as JsonObject
                            titles.add(item["name"].asString.trim { it <= ' ' })
                        }
                    } catch (th: Throwable) {
                        LogUtils.d(th.toString())
                    }
                    if (titles.isNotEmpty()) {
                        showSuggestDialog(titles)
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String {
                    return response.body()?.string() ?: ""
                }
            })
    }

    private var mSearchSuggestionsDialog: SearchSuggestionsDialog? = null
    private fun showSuggestDialog(list: List<String>) {
        if (mSearchSuggestionsDialog == null) {
            mSearchSuggestionsDialog = SearchSuggestionsDialog(
                this@FastSearchActivity, list
            ) { _: Int, text: String ->
                mSearchSuggestionsDialog?.dismissWith { search(text) }
            }

            XPopup.Builder(this@FastSearchActivity).atView(mBinding.etSearch)
                .notDismissWhenTouchInView(mBinding.etSearch).isViewMode(true) //开启View实现
                .isRequestFocus(false) //不强制焦点
                .setPopupCallback(object : SimpleCallback() {
                    override fun onDismiss(popupView: BasePopupView) {
                        // 弹窗关闭了就置空对象,下次重新new
                        super.onDismiss(popupView)
                        mSearchSuggestionsDialog = null
                    }
                }).asCustom(mSearchSuggestionsDialog).show()
        } else {
            // 不为空说明弹窗为打开状态(关闭就置空了).直接刷新数据
            mSearchSuggestionsDialog?.updateSuggestions(list)
        }
    }

    private fun saveSearchHistory(searchWord: String) {
        if (searchWord.isNotEmpty()) {
            val history = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList<String?>())
            if (!history.contains(searchWord)) {
                history.add(0, searchWord)
            } else {
                history.remove(searchWord)
                history.add(0, searchWord)
            }
            if (history.size > 30) {
                history.removeAt(30)
            }
            Hawk.put(HawkConfig.HISTORY_SEARCH, history)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            val title = event.obj as String
            showLoading()
            search(title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(if (event.obj == null) null else event.obj as AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    private fun initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch()
    }

    private fun search(title: String) {
        if (TextUtils.isEmpty(title)) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }

        //先移除监听,避免重新设置要搜索的文字触发搜索建议并弹窗
        mBinding.etSearch.removeTextChangedListener(this)
        mBinding.etSearch.setText(title)
        mBinding.etSearch.setSelection(title.length)
        mBinding.etSearch.addTextChangedListener(this)

        if (mSearchSuggestionsDialog?.isShow == false) {
            mSearchSuggestionsDialog?.dismiss()
        }

        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {
            //无痕浏览不存搜索历史
            saveSearchHistory(title)
        }
        hideHotAndHistorySearch(true)
        KeyboardUtils.hideSoftInput(this)
        cancel()
        showLoading()
        this.searchTitle = title
        mBinding.mGridView.visibility = View.INVISIBLE
        mBinding.mGridViewFilter.visibility = View.GONE
        searchAdapter.setNewData(ArrayList())
        searchAdapterFilter.setNewData(ArrayList())

        resultVideos.clear()
        searchFilterKey = ""
        isFilterMode = false
        spNames.clear()
        mBinding.tabLayout.removeAllTabs()
        searchResult()
    }

    private fun searchResult() {
        try {
            searchExecutorService.shutdownNow()
            JsLoader.stopAll()
        } catch (ignored: Throwable) {
        } finally {
            searchAdapter.setNewData(ArrayList())
            searchAdapterFilter.setNewData(ArrayList())
            allRunCount.set(0)
        }
        searchExecutorService = Executors.newFixedThreadPool(10)
        val searchRequestList: MutableList<SourceBean> = ArrayList(ApiConfig.get().sourceBeanList)
        val home = ApiConfig.get().homeSourceBean
        searchRequestList.remove(home)
        searchRequestList.add(0, home)
        mBinding.tabLayout.removeAllTabs()
        val siteKey = ArrayList<String>()
        val tab = mBinding.tabLayout.newTab()
        tab.text = "全部显示"
        mBinding.tabLayout.addTab(tab)
//        mBinding.tabLayout.setCurrentItem(0, notify = true, fromUser = false)
        for (bean in searchRequestList) {
            if (!bean.isSearchable) {
                continue
            }
            if (!mCheckSources.containsKey(bean.key)) {
                continue
            }
            siteKey.add(bean.key)
            spNames[bean.name] = bean.key
            allRunCount.incrementAndGet()
        }

        for (key in siteKey) {
            searchExecutorService.execute {
                try {
                    sourceViewModel.getSearch(key, searchTitle)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * 添加到最后面并返回最后一个key
     *
     * @param key
     * @return
     */
    private fun addWordAdapterIfNeed(key: String): String {
        try {
            var name = ""
            for (n in spNames.keys) {
                if (spNames[n] == key) {
                    name = n
                }
            }
            if (name == "") return key
            for (i in 0 until mBinding.tabLayout.childCount) {
                val item = mBinding.tabLayout.getTabAt(i)
                if (Objects.equals(name, item?.text.toString())) {
                    return key
                }
            }
            mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(name))
            return key
        } catch (e: java.lang.Exception) {
            return key
        }
    }

    private fun matchSearchResult(name: String, searchTitle: String): Boolean {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false
        val arr =
            searchTitle.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        var matchNum = 0
        for (one in arr) {
            if (name.contains(one)) matchNum++
        }
        return matchNum == arr.size
    }

    private fun searchData(absXml: AbsXml?) {
        var lastSourceKey = ""

        if (absXml?.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.isNotEmpty()) {
            val data: MutableList<Movie.Video> = ArrayList()
            for (video in absXml.movie.videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                data.add(video)
                if (!resultVideos.containsKey(video.sourceKey)) {
                    resultVideos[video.sourceKey] = ArrayList()
                }
                resultVideos[video.sourceKey]?.add(video)
                if (video.sourceKey !== lastSourceKey) {
                    // 添加到最后面并记录最后一个key用于下次判断
                    lastSourceKey = this.addWordAdapterIfNeed(video.sourceKey)
                }
            }

            if (searchAdapter.data.isNotEmpty()) {
                searchAdapter.addData(data)
            } else {
                showSuccess()
                if (!isFilterMode) mBinding.mGridView.visibility = View.VISIBLE
                searchAdapter.setNewData(data)
            }
        }

        val count = allRunCount.decrementAndGet()
        if (count <= 0) {
            if (searchAdapter.data.size <= 0) {
                showEmpty()
            }
            cancel()
        }
    }

    private fun cancel() {
        OkGo.getInstance().cancelTag("search")
        OkGo.getInstance().cancelTag("queryFromTMDB")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        try {
            searchExecutorService.shutdownNow()
            JsLoader.load()
        } catch (ignored: Throwable) {
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
    }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
    }

    override fun afterTextChanged(editable: Editable) {
        val text = editable.toString()
        if (TextUtils.isEmpty(text)) {
            mSearchSuggestionsDialog?.dismiss()
        } else {
            getSuggest(text)
        }
    }

    /**
     * 查询影片的信息
     *
     * @param vodName
     */
    private fun queryFromTMDB(vodName: String) {
        OkGo.getInstance().cancelTag("queryFromTMDB")

        val token = Hawk.get(HawkConfig.TOKEN_TMDB, "")
        if (TextUtils.isEmpty(token)) {
            return
        }
        showLoadingDialog()
        OkGo.get<String>("https://api.themoviedb.org/3/search/movie?query=$vodName&include_adult=false&language=zh-ZH&page=1")
            .headers("Authorization", "Bearer $token").tag("queryFromTMDB")
            .execute(object : AbsCallback<String?>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String {
                    if (response.body() != null) {
                        return response.body()!!.string()
                    } else {
                        throw IllegalStateException("网络请求错误")
                    }
                }

                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    dismissLoadingDialog()
                    val json = response.body()
                    val videoInfo = GsonUtils.fromJson(json, TmdbVodInfo::class.java)
                    val results = videoInfo.results
                    if (results != null && results.isNotEmpty()) {
                        XPopup.Builder(this@FastSearchActivity)
                            .asCustom(TmdbVodInfoDialog(this@FastSearchActivity, results[0])).show()
                    } else {
                        ToastUtils.showShort("未查询到相关信息")
                    }
                }

                override fun onError(response: com.lzy.okgo.model.Response<String?>) {
                    super.onError(response)
                    dismissLoadingDialog()
                }
            })
    }

    companion object {
        private var mCheckSources: HashMap<String, String> = HashMap()
        fun setCheckedSourcesForSearch(checkedSources: HashMap<String, String>) {
            mCheckSources = checkedSources
        }
    }
}