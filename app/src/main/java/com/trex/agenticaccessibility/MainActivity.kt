package com.trex.agenticaccessibility

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.trex.agenticaccessibility.agent.AgentController
import com.trex.agenticaccessibility.security.SecureStore
import com.trex.agenticaccessibility.voice.AndroidSpeechToText
import com.trex.agenticaccessibility.voice.AndroidTextToSpeech
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: AgentController
    private lateinit var task: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store=SecureStore(this); controller=AgentController(this,store,AndroidTextToSpeech(this))
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,28,28,28)}
        val status=TextView(this).apply{text="● Ready";textSize=20f}; task=EditText(this).apply{hint="Tell the agent what to do…";minLines=3;gravity=48}
        val endpoint=EditText(this).apply{hint="OpenAI-compatible endpoint";setText(store.get("endpoint")?:"https://api.openai.com/v1/chat/completions")}
        val model=EditText(this).apply{hint="Model";setText(store.get("model")?:"gpt-4o-mini")}
        val key=EditText(this).apply{hint="API key";inputType=0x81}; val start=Button(this).apply{text="Start Agent"}; val stop=Button(this).apply{text="Stop"}; val access=Button(this).apply{text="Accessibility Settings"}; val mic=Button(this).apply{text="🎙 Push to talk"}; val log=TextView(this).apply{text="Activity log\n"}
        listOf(status,task,endpoint,model,key,start,stop,access,mic,log).forEach{root.addView(it)};setContentView(root)
        access.setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        start.setOnClickListener{store.put("endpoint",endpoint.text.toString().trim());store.put("model",model.text.toString().trim());if(key.text.isNotBlank())store.put("api_key",key.text.toString());controller.start(task.text.toString())}
        stop.setOnClickListener{controller.stop();status.text="■ Stopped"}
        mic.setOnClickListener{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),42)else listen()}
        lifecycleScope.launch{controller.events.collect{status.text=it.status;log.text="Activity log\n"+it.log.joinToString("\n")}}
    }
    private fun listen(){AndroidSpeechToText(this).listen{spoken->runOnUiThread{task.setText(spoken);controller.start(spoken)}}}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==42&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)listen()}
    override fun onDestroy(){controller.stop();super.onDestroy()}
}
