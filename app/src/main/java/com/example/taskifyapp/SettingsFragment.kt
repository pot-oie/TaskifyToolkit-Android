package com.example.taskifyapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.taskifyapp.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        // 初始化 SharedPreferences
        prefs = requireActivity().getSharedPreferences("TaskifySettings", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        val savedUrl = prefs.getString("server_url", "")
        val autoReconnect = prefs.getBoolean("auto_reconnect", true)

        binding.serverUrlEditText.setText(savedUrl)
        binding.autoReconnectSwitch.isChecked = autoReconnect
    }

    private fun setupListeners() {
        binding.saveUrlButton.setOnClickListener {
            val url = binding.serverUrlEditText.text.toString().trim()
            prefs.edit().putString("server_url", url).apply()
            Toast.makeText(requireContext(), "服务器地址已保存", Toast.LENGTH_SHORT).show()
        }

        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_reconnect", isChecked).apply()
            val status = if (isChecked) "开启" else "关闭"
            Toast.makeText(requireContext(), "自动重连已$status", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}