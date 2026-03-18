package com.blindvision.client

import android.app.Application
import com.blindvision.client.domain.DecisionScheduler

class BlindVisionApp : Application() {
    
    lateinit var decisionScheduler: DecisionScheduler
        private set
    
    override fun onCreate() {
        super.onCreate()
        decisionScheduler = DecisionScheduler(this)
        decisionScheduler.initialize()
    }
    
    override fun onTerminate() {
        decisionScheduler.cleanup()
        super.onTerminate()
    }
}
