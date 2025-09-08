package com.example.taskifyapp.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taskifyapp.databinding.ActivityKeepAliveGuideBinding

class KeepAliveGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeepAliveGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeepAliveGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 显示返回箭头
    }

    // 处理返回箭头的点击事件
    override fun onSupportNavigateUp(): Boolean {
        finish() // 关闭当前页面
        return true
    }
}