package com.example.taskifyapp.ui.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.taskifyapp.databinding.FragmentSettingsBinding
import com.example.taskifyapp.viewmodel.AgentViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels() 获取与 Activity 共享的 ViewModel 实例
    private val viewModel: AgentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
    }

    /**
     * 观察 ViewModel 中的设置状态
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察 uiState，并过滤掉 settings 为 null 的初始情况
                viewModel.uiState.filterNotNull().collect { state ->
                    binding.currentServerIpTextView.text = state.settings?.serverIp ?: "N/A"
                    binding.autoReconnectSwitch.isChecked = state.settings?.autoReconnect ?: true
                }
            }
        }
    }

    private fun setupListeners() {
        // 为输入框添加焦点变化监听器
        binding.serverIpEditText.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onEditTextFocused(hasFocus)
        }

        // 按钮点击逻辑
        binding.saveUrlButton.setOnClickListener {
            val computerIp = binding.serverIpEditText.text.toString().trim()

            if (computerIp.isEmpty()) {
                Toast.makeText(requireContext(), "电脑IP地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 将保存操作委托给 ViewModel
            viewModel.onSaveSettingsClicked(computerIp, binding.autoReconnectSwitch.isChecked)
            binding.serverIpEditText.setText("") // 清空输入框
        }

        // 开关监听逻辑
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            val currentIp = binding.currentServerIpTextView.text.toString()
            // 开关变化也触发保存
            viewModel.onSaveSettingsClicked(currentIp, isChecked)
            val status = if (isChecked) "开启" else "关闭"
            Toast.makeText(requireContext(), "自动重连已$status", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}