package com.example.taskifyapp.ui.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.taskifyapp.databinding.FragmentInfoBinding
import com.example.taskifyapp.util.DeviceUtils

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
        binding.deviceModelTextView.text = "设备型号: ${DeviceUtils.getDeviceModel()}"
        binding.androidVersionTextView.text = "安卓版本: ${DeviceUtils.getAndroidVersion()}"
        binding.deviceIpTextView.text = "本机IP: ${DeviceUtils.getDeviceIpAddress(requireContext())}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}