package com.example.taskifyapp.ui.dialog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taskifyapp.databinding.ActivityCompletionDialogBinding

class CompletionDialogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompletionDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用 ViewBinding 加载我们自己绘制的对话框布局
        binding = ActivityCompletionDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 为“确定”按钮设置点击事件，点击后关闭页面
        binding.buttonOk.setOnClickListener {
            finish()
        }

        // 点击半透明背景区域也关闭页面
        binding.rootLayout.setOnClickListener {
            finish()
        }
    }
}