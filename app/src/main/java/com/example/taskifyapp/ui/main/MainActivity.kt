package com.example.taskifyapp.ui.main

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.taskifyapp.databinding.ActivityMainBinding
import com.example.taskifyapp.repository.AgentRepository
import com.example.taskifyapp.ui.main.adapter.ViewPagerAdapter
import com.example.taskifyapp.ui.state.AgentUiState
import com.example.taskifyapp.ui.state.ViewEvent
import com.example.taskifyapp.viewmodel.AgentViewModel
import com.example.taskifyapp.viewmodel.AgentViewModelFactory
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // 创建 ViewModel 实例
    private val viewModel: AgentViewModel by lazy {
        val repository = AgentRepository(application)
        val factory = AgentViewModelFactory(repository)
        ViewModelProvider(this, factory)[AgentViewModel::class.java]
    }

    // 用于处理截图权限的 ActivityResultLauncher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // ViewModel 处理
        viewModel.onScreenCapturePermissionResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupCollapsibleSections() // 折叠功能
        setupButtonListeners() // 监听器只转发事件

        observeUiState()   // 观察UI状态
        observeViewEvents()// 观察一次性事件
    }

    override fun onResume() {
        super.onResume()
        // 每次返回应用时，都让ViewModel刷新一次状态，确保无障碍服务状态是最新的
        viewModel.refreshStates()
    }

    /**
     * 观察ViewModel的统一UI状态流 (StateFlow)
     */
    private fun observeUiState() {
        lifecycleScope.launch {
            // 当生命周期至少为 STARTED 时，此协程会执行，并在 STOPPED 时挂起
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state) // 用最新的状态来渲染整个UI
                }
            }
        }
    }

    /**
     * 观察ViewModel的一次性事件流 (SharedFlow)
     */
    private fun observeViewEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ViewEvent.RequestScreenCapture -> {
                            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                        is ViewEvent.OpenAccessibilitySettings -> {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        is ViewEvent.ShowToast -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据 ViewModel 提供的 AgentUiState 来渲染整个UI
     */
    private fun render(state: AgentUiState) {
        // 更新无障碍服务状态
        binding.statusAccessibility.text = state.accessibilityStatusText
        binding.statusAccessibility.setTextColor(ContextCompat.getColor(this, state.accessibilityStatusColor))
        binding.iconAccessibilityStatus.setImageResource(state.accessibilityStatusIcon)

        // 更新WebSocket服务状态
        binding.statusWebsocket.text = state.webSocketStatusText
        binding.statusWebsocket.setTextColor(ContextCompat.getColor(this, state.webSocketStatusColor))
        binding.iconWebsocketStatus.setImageResource(state.webSocketStatusIcon)

        // 更新按钮文本和颜色
        binding.buttonAccessibilityToggle.text = state.accessibilityButtonText
        binding.buttonServiceToggle.text = state.serviceButtonText
        binding.buttonServiceToggle.setBackgroundColor(ContextCompat.getColor(this, state.serviceButtonColor))

        // 处理折叠面板逻辑
        if (state.shouldCollapsePanels) {
            collapseAllPanels()
            viewModel.onPanelsCollapsed() // 通知ViewModel事件已消耗
        }
    }

    /**
     * 监听器将用户操作通知给 ViewModel
     */
    private fun setupButtonListeners() {
        // 无障碍服务按钮
        binding.buttonAccessibilityToggle.setOnClickListener {
            viewModel.onAccessibilityToggleClicked()
        }

        // 后台服务按钮
        binding.buttonServiceToggle.setOnClickListener {
            viewModel.onServiceToggleClicked()
        }

        // 帮助指南按钮
        binding.buttonHelpGuide.setOnClickListener {
            val intent = Intent(this, KeepAliveGuideActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupTabs() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "实时日志"
                1 -> "高级设置"
                else -> "设备信息"
            }
        }.attach()
    }

    private fun setupCollapsibleSections() {
        // 箭头先旋转180度
        binding.ivToggleStatus.rotation = 180f
        binding.ivToggleActions.rotation = 180f

        // 为“服务状态”板块设置点击事件
        binding.headerStatus.setOnClickListener {
            toggleSection(binding.contentStatus, binding.ivToggleStatus)
        }

        // 为“核心操作”板块设置点击事件
        binding.headerActions.setOnClickListener {
            toggleSection(binding.contentActions, binding.ivToggleActions)
        }
    }

    /**
     * 切换板块的展开/收起状态
     * @param contentLayout 要被隐藏/显示的内容区域
     * @param arrow         用于指示状态的箭头ImageView
     */
    private fun toggleSection(contentLayout: LinearLayout, arrow: ImageView) {
        val isVisible = contentLayout.visibility == View.VISIBLE

        // 切换可见性
        contentLayout.visibility = if (isVisible) View.GONE else View.VISIBLE

        // 切换逻辑
        if (isVisible) {
            // 如果当前可见，隐藏内容，旋转箭头
            contentLayout.visibility = View.GONE
            arrow.animate().rotation(0f).setDuration(200).start()
        } else {
            // 如果当前隐藏，显示内容，旋转箭头
            contentLayout.visibility = View.VISIBLE
            arrow.animate().rotation(180f).setDuration(200).start()
        }
    }

    /**
     * 收起所有面板
     */
    private fun collapseAllPanels() {
        // “服务状态”
        if (binding.contentStatus.visibility == View.VISIBLE) {
            toggleSection(binding.contentStatus, binding.ivToggleStatus)
        }
        // “核心操作”
        if (binding.contentActions.visibility == View.VISIBLE) {
            toggleSection(binding.contentActions, binding.ivToggleActions)
        }
    }
}
