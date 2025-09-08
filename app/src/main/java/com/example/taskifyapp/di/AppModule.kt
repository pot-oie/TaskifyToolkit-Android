package com.example.taskifyapp.di
//等学完基础内容再学习hilt框架
//import android.content.Context
//import com.example.taskifyapp.repository.AgentRepository
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.android.qualifiers.ApplicationContext
//import dagger.hilt.components.SingletonComponent
//import javax.inject.Singleton
//
//@Module
//@InstallIn(SingletonComponent::class) // 表示这个模块中的依赖在整个应用的生命周期内有效
//object AppModule {
//
//    /**
//     * 这是告诉Hilt如何“生产” AgentRepository 的说明。
//     * @Provides 注解表示“这是一个提供依赖的方法”。
//     * @Singleton 注解表示这个依赖在应用中全局唯一。
//     * @ApplicationContext 注解告诉Hilt自动注入全局的Application Context。
//     */
//    @Provides
//    @Singleton
//    fun provideAgentRepository(@ApplicationContext context: Context): AgentRepository {
//        return AgentRepository(context)
//    }
//}