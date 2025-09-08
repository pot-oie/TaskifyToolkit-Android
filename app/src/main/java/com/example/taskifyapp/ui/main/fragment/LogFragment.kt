package com.example.taskifyapp.ui.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.taskifyapp.databinding.FragmentLogBinding
import com.example.taskifyapp.viewmodel.AgentViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class LogFragment : Fragment() {
    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AgentViewModel by activityViewModels()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeLogs()
    }

    /**
     * 观察ViewModel中的日志列表状态
     */
    private fun observeLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 每当ViewModel中的日志列表更新时，这里都会收到通知
                viewModel.uiState.collect { state ->
                    // 从完整的日志列表构建显示的字符串
                    val logText = state.logs.joinToString("\n") { logMessage ->
                        "[${sdf.format(Date())}] $logMessage"
                    }
                    binding.logTextView.text = logText

                    // 自动滚动到底部
                    binding.logScrollView.post {
                        binding.logScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}