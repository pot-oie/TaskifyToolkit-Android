package com.example.taskifyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.taskifyapp.repository.AgentRepository

/**
 * 一个用于创建 AgentViewModel 实例的工厂类。
 * 当 ViewModel 有构造函数参数时，就需要这样的工厂。
 */
class AgentViewModelFactory(private val repository: AgentRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            return AgentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}