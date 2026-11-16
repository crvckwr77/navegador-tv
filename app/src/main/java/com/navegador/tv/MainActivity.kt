package com.navegador.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

data class BrowserTab(
    val id: Long,
    var title: String,
    var url: String,
    var blockedCount: Int,
    val webView: WebView
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "https://www.google.com.ar"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val AD_DOMAINS = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagmanager.com",
            "amazon-adsystem.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "criteo.net",
            "adnxs.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net",
            "scorecardresearch.com",
            "hotjar.com",
            "clarity.ms",
            "quantserve.com",
            "chartbeat.com",
            "connect.facebook.net",
            "analytics.tiktok.com",
            "ads-twitter.com",
            "bat.bing.com",
            "mgid.com",
            "revcontent.com",
            "mediavine.com",
            "adthrive.com",
            "zedo.com",
            "adroll.com",
            "adsafeprotected.com",
            "moatads.com",
            "casalemedia.com",
            "popads.net",
            "propellerads.com",
            "adsterra.com",
            "pagead2.googlesyndication.com"
        )
    }

    private lateinit var tabStrip: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarDivider: View
    private lateinit var omniboxContainer: LinearLayout
    private lateinit var tabsRecycler: RecyclerView
    private lateinit var webContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var editUrl: EditText
    private lateinit var iconSearch: TextView
    private lateinit var txtPcMode: TextView
    private lateinit var txtCloseIcon: TextView
    private lateinit var txtCloseLabel: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var btnReload: TextView
    private lateinit var btnNewTab: TextView
    private lateinit var btnBookmark: TextView
    private lateinit var btnAdBlock: TextView
    private lateinit var btnBookmarksList: TextView
    private lateinit var btnTheme: TextView
    private lateinit var btnFullscreen: TextView
    private lateinit var btnClose: LinearLayout
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private lateinit var tabAdapter: TabAdapter

    private var isDarkMode = true
    private var adBlockEnabled = true
    private var totalSessionBlocked = 0
    private var isImmersiveBrowser = false

    private var customVideoView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val prefs by lazy { getSharedPreferences("navegador_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        isDarkMode = prefs.getBoolean("dark_mode", true)
        adBlockEnabled = prefs.getBoolean("adblock_enabled", true)
        totalSessionBlocked = prefs.getInt("total_blocked", 0)

        bindViews()
        setupTabsRecycler()
        setupControls()
        setupBackNavigation()

        applyTheme()
        createNewTab(HOME_URL)
    }

    private fun bindViews() {
        tabStrip = findViewById(R.id.tabStrip)
        toolbar = findViewById(R.id.toolbar)
        toolbarDivider = findViewById(R.id.toolbarDivider)
        omniboxContainer = findViewById(R.id.omniboxContainer)
        tabsRecycler = findViewById(R.id.tabsRecycler)
        webContainer = findViewById(R.id.webContainer)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        progressBar = findViewById(R.id.progressBar)
        editUrl = findViewById(R.id.editUrl)
        iconSearch = findViewById(R.id.iconSearch)
        txtPcMode = findViewById(R.id.txtPcMode)
        txtCloseIcon = findViewById(R.id.txtCloseIcon)
        txtCloseLabel = findViewById(R.id.txtCloseLabel)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnReload = findViewById(R.id.btnReload)
        btnNewTab = findViewById(R.id.btnNewTab)
        btnBookmark = findViewById(R.id.btnBookmark)
        btnAdBlock = findViewById(R.id.btnAdBlock)
        btnBookmarksList = findViewById(R.id.btnBookmarksList)
        btnTheme = findViewById(R.id.btnTheme)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnClose = findViewById(R.id.btnClose)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
    }

    private fun setupTabsRecycler() {
        tabAdapter = TabAdapter(
            onSelect = { index -> switchToTab(index) },
            onClose = { index -> closeTab(index) }
        )
        tabsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        tabsRecycler.adapter = tabAdapter
    }

    private fun setupControls() {
        btnBack.setOnClickListener {
            currentWebView()?.let { if (it.canGoBack()) it.goBack() }
        }
        btnForward.setOnClickListener {
            currentWebView()?.let { if (it.canGoForward()) it.goForward() }
        }
        btnReload.setOnClickListener {
            currentWebView()?.reload()
        }
        btnNewTab.setOnClickListener {
            createNewTab(HOME_URL)
        }
        btnClose.setOnClickListener {
            if (tabs.size > 1) {
                closeTab(activeTabIndex)
            } else {
                finish()
            }
        }
        btnTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
            applyTheme()
        }
        btnAdBlock.setOnClickListener {
            showAdBlockDialog()
        }
        btnBookmark.setOnClickListener {
            toggleCurrentBookmark()
        }
        btnBookmarksList.setOnClickListener {
            showBookmarksDialog()
        }
        btnFullscreen.setOnClickListener {
            toggleImmersiveBrowser()
        }

        editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateFromInput(editUrl.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customVideoView != null) {
                    exitVideoFullscreen()
                    return
                }
                if (isImmersiveBrowser) {
                    toggleImmersiveBrowser()
                    return
                }
                val web = currentWebView()
                if (web != null && web.canGoBack()) {
                    web.goBack()
                    return
                }
                if (tabs.size > 1) {
                    closeTab(activeTabIndex)
                    return
                }
                finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(tabId: Long): WebView {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.userAgentString = DESKTOP_USER_AGENT

        applyDarkSettingToWebView(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (!adBlockEnabled || request == null) return super.shouldInterceptRequest(view, request)
                val host = request.url?.host?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                if (isAdHost(host)) {
                    runOnUiThread {
                        val tab = tabs.find { it.id == tabId }
                        if (tab != null) {
                            tab.blockedCount += 1
                            totalSessionBlocked += 1
                            prefs.edit().putInt("total_blocked", totalSessionBlocked).apply()
                            if (currentTab()?.id == tabId) {
                                updateTopState()
                            }
                        }
                    }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return !(url.startsWith("http://") || url.startsWith("https://"))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val tab = tabs.find { it.id == tabId } ?: return
                if (!url.isNullOrBlank()) {
                    tab.url = url
                }
                if (currentTab()?.id == tabId) {
                    editUrl.setText(tab.url)
                    updateTopState()
                }
                injectPcAndCleanupScripts(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (currentTab()?.id == tabId) {
                    if (newProgress in 1..99) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                val tab = tabs.find { it.id == tabId } ?: return
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    tabAdapter.notifyDataSetChanged()
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customVideoView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customVideoView = view
                customViewCallback = callback
                fullscreenContainer.removeAllViews()
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                setSystemFullscreen(true)
            }

            override fun onHideCustomView() {
                exitVideoFullscreen()
            }
        }

        return webView
    }

    private fun exitVideoFullscreen() {
        if (customVideoView == null) return
        fullscreenContainer.visibility = View.GONE
        fullscreenContainer.removeAllViews()
        customVideoView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        if (!isImmersiveBrowser) {
            setSystemFullscreen(false)
        }
    }

    private fun toggleImmersiveBrowser() {
        isImmersiveBrowser = !isImmersiveBrowser
        tabStrip.visibility = if (isImmersiveBrowser) View.GONE else View.VISIBLE
        toolbar.visibility = if (isImmersiveBrowser) View.GONE else View.VISIBLE
        toolbarDivider.visibility = if (isImmersiveBrowser) View.GONE else View.VISIBLE
        setSystemFullscreen(isImmersiveBrowser)
    }

    private fun setSystemFullscreen(enable: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun injectPcAndCleanupScripts(webView: WebView?) {
        if (webView == null) return
        val adCss = if (adBlockEnabled) {
            "ins.adsbygoogle,[id^='google_ads'],[id*='div-gpt-ad'],[class*='ad-banner'],[data-ad-slot],amp-ad{display:none!important;}"
        } else {
            ""
        }
        val darkCss = if (isDarkMode && !WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            "html,body{background:#0e1117!important;color:#e6edf3!important;}a{color:#60a5fa!important;}"
        } else {
            ""
        }
        val js = """
            (function() {
                var vp = document.querySelector('meta[name="viewport"]');
                if (vp) vp.setAttribute('content', 'width=1280, initial-scale=1.0');
                var old = document.getElementById('navegador-injected-style');
                if (old) old.remove();
                var st = document.createElement('style');
                st.id = 'navegador-injected-style';
                st.textContent = "$adCss $darkCss";
                (document.head || document.documentElement).appendChild(st);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun isAdHost(host: String): Boolean {
        for (ad in AD_DOMAINS) {
            if (host == ad || host.endsWith(".$ad")) return true
        }
        return false
    }

    private fun createNewTab(url: String) {
        val id = System.currentTimeMillis()
        val webView = createConfiguredWebView(id)
        val tab = BrowserTab(
            id = id,
            title = "Navegador",
            url = url,
            blockedCount = 0,
            webView = webView
        )
        tabs.add(tab)
        switchToTab(tabs.lastIndex)
        webView.loadUrl(url)
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        webContainer.removeAllViews()
        val tab = tabs[activeTabIndex]
        webContainer.addView(tab.webView)
        editUrl.setText(tab.url)
        tabAdapter.notifyDataSetChanged()
        updateTopState()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        removed.webView.destroy()
        if (tabs.isEmpty()) {
            createNewTab(HOME_URL)
            return
        }
        val nextIndex = index.coerceAtMost(tabs.lastIndex)
        switchToTab(nextIndex)
    }

    private fun navigateFromInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val targetUrl = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
            else -> "https://www.google.com.ar/search?q=${Uri.encode(trimmed)}&hl=es-419&gl=AR"
        }
        val tab = currentTab() ?: return
        tab.blockedCount = 0
        tab.webView.loadUrl(targetUrl)
    }

    private fun currentTab(): BrowserTab? = tabs.getOrNull(activeTabIndex)
    private fun currentWebView(): WebView? = currentTab()?.webView

    private fun updateTopState() {
        val tab = currentTab()
        val web = tab?.webView
        btnBack.alpha = if (web?.canGoBack() == true) 1.0f else 0.35f
        btnForward.alpha = if (web?.canGoForward() == true) 1.0f else 0.35f
        val count = tab?.blockedCount ?: 0
        btnAdBlock.text = if (adBlockEnabled) "🛡 $count" else "🛡 OFF"
        btnBookmark.text = if (tab != null && isBookmarked(tab.url)) "★" else "☆"
        btnBookmark.setTextColor(
            if (tab != null && isBookmarked(tab.url)) Color.parseColor("#F59E0B")
            else if (isDarkMode) Color.parseColor("#CBD5E1")
            else Color.parseColor("#475569")
        )
    }

    private fun applyDarkSettingToWebView(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDarkMode)
        }
        webView.setBackgroundColor(if (isDarkMode) Color.parseColor("#0E1117") else Color.WHITE)
    }

    private fun applyTheme() {
        val stripBg = if (isDarkMode) Color.parseColor("#13161E") else Color.parseColor("#DFE3E8")
        val barBg = if (isDarkMode) Color.parseColor("#1E222B") else Color.parseColor("#FFFFFF")
        val omniboxBg = if (isDarkMode) Color.parseColor("#13161E") else Color.parseColor("#F1F3F4")
        val dividerBg = if (isDarkMode) Color.parseColor("#2A303D") else Color.parseColor("#E2E8F0")
        val textPrimary = if (isDarkMode) Color.parseColor("#F1F5F9") else Color.parseColor("#111827")
        val textMuted = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val dotColor = if (isDarkMode) Color.parseColor("#4B5260") else Color.parseColor("#64686E")

        tabStrip.setBackgroundColor(stripBg)
        toolbar.setBackgroundColor(barBg)
        toolbarDivider.setBackgroundColor(dividerBg)

        val omniboxShape = GradientDrawable().apply {
            setColor(omniboxBg)
            cornerRadius = 999f
        }
        omniboxContainer.background = omniboxShape

        val dotShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        dot1.background = dotShape
        dot2.background = dotShape.constantState?.newDrawable()
        dot3.background = dotShape.constantState?.newDrawable()

        editUrl.setTextColor(textPrimary)
        editUrl.setHintTextColor(textMuted)
        iconSearch.setTextColor(textMuted)
        txtPcMode.setTextColor(textMuted)
        btnBack.setTextColor(textPrimary)
        btnForward.setTextColor(textPrimary)
        btnReload.setTextColor(textPrimary)
        btnNewTab.setTextColor(textPrimary)
        btnAdBlock.setTextColor(
            if (adBlockEnabled) Color.parseColor("#10B981") else textMuted
        )
        btnBookmarksList.setTextColor(textPrimary)
        btnTheme.setTextColor(textPrimary)
        btnTheme.text = if (isDarkMode) "☀️" else "🌙"
        btnFullscreen.setTextColor(textPrimary)
        txtCloseIcon.setTextColor(textPrimary)
        txtCloseLabel.setTextColor(textPrimary)

        tabs.forEach {
            applyDarkSettingToWebView(it.webView)
            injectPcAndCleanupScripts(it.webView)
        }
        tabAdapter.notifyDataSetChanged()
        updateTopState()
    }

    private fun getBookmarks(): MutableList<Pair<String, String>> {
        val raw = prefs.getString("bookmarks", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(obj.getString("title") to obj.getString("url"))
        }
        return list
    }

    private fun saveBookmarks(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        for ((title, url) in list) {
            arr.put(JSONObject().put("title", title).put("url", url))
        }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    private fun isBookmarked(url: String): Boolean = getBookmarks().any { it.second == url }

    private fun toggleCurrentBookmark() {
        val tab = currentTab() ?: return
        val list = getBookmarks()
        if (list.any { it.second == tab.url }) {
            saveBookmarks(list.filterNot { it.second == tab.url })
        } else {
            list.add(0, tab.title to tab.url)
            saveBookmarks(list)
        }
        updateTopState()
    }

    private fun showBookmarksDialog() {
        val list = getBookmarks()
        if (list.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Marcadores")
                .setMessage("No tienes marcadores guardados todavía. Usa ☆ en la barra para guardar una página.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }
        val labels = list.map { "${it.first}\n${it.second}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Marcadores")
            .setItems(labels) { _, which ->
                currentWebView()?.loadUrl(list[which].second)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showAdBlockDialog() {
        val currentCount = currentTab()?.blockedCount ?: 0
        val status = if (adBlockEnabled) "Activo" else "Pausado"
        AlertDialog.Builder(this)
            .setTitle("Bloqueador de anuncios nativo")
            .setMessage(
                "Estado: $status\n" +
                    "Bloqueados en esta página: $currentCount\n" +
                    "Total bloqueados en sesión: $totalSessionBlocked"
            )
            .setPositiveButton(if (adBlockEnabled) "Pausar bloqueo" else "Activar bloqueo") { _, _ ->
                adBlockEnabled = !adBlockEnabled
                prefs.edit().putBoolean("adblock_enabled", adBlockEnabled).apply()
                applyTheme()
                currentWebView()?.reload()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editUrl.windowToken, 0)
    }

    inner class TabAdapter(
        private val onSelect: (Int) -> Unit,
        private val onClose: (Int) -> Unit
    ) : RecyclerView.Adapter<TabAdapter.TabHolder>() {

        inner class TabHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: LinearLayout = view.findViewById(R.id.tabRoot)
            val title: TextView = view.findViewById(R.id.tabTitle)
            val close: TextView = view.findViewById(R.id.tabClose)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
            return TabHolder(view)
        }

        override fun getItemCount(): Int = tabs.size

        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            val tab = tabs[position]
            val isActive = position == activeTabIndex
            holder.title.text = tab.title

            val activeBg = if (isDarkMode) Color.parseColor("#1E222B") else Color.parseColor("#FFFFFF")
            val inactiveBg = Color.TRANSPARENT
            val textColor = if (isActive) {
                if (isDarkMode) Color.parseColor("#F1F5F9") else Color.parseColor("#111827")
            } else {
                if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
            }

            val bg = GradientDrawable().apply {
                setColor(if (isActive) activeBg else inactiveBg)
                cornerRadii = floatArrayOf(24f, 24f, 24f, 24f, 0f, 0f, 0f, 0f)
            }
            holder.root.background = bg
            holder.title.setTextColor(textColor)
            holder.close.setTextColor(textColor)

            holder.root.setOnClickListener { onSelect(holder.bindingAdapterPosition) }
            holder.close.setOnClickListener { onClose(holder.bindingAdapterPosition) }
        }
    }
}
