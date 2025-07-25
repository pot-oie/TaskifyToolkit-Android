package com.example.taskifyapp

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.taskifyapp.databinding.FragmentInfoBinding
import java.util.*

class InfoFragment : Fragment() {
    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 当View创建完成后，我们在这里填充信息
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayDeviceInfo()
    }

    private fun displayDeviceInfo() {
        binding.deviceModelTextView.text = "设备型号: ${Build.MODEL}"
        binding.androidVersionTextView.text = "安卓版本: ${Build.VERSION.RELEASE}"
        binding.deviceIpTextView.text = "本机IP: ${getDeviceIpAddress()}"
    }

    private fun getDeviceIpAddress(): String {
        try {
            val wifiManager = requireActivity().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress == 0) return "未连接到WiFi"
            return String.format(
                Locale.getDefault(), "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) {
            return "获取失败"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}