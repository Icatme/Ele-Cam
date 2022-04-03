package com.example.ele_cam

//import kotlinx.coroutines.
//import java.net.http.


import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.example.ele_cam.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )

        val camv = binding.webView
        camv.settings.loadsImagesAutomatically = true
        camv.settings.javaScriptEnabled = true
        camv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, murl: String): Boolean {
                view?.loadUrl(murl)
                return true
            }
        }
        camv.loadUrl("http://192.168.1.254:8192")
        camv.setOnClickListener {
            camv.loadUrl("http://192.168.1.254:8192")
            camv.reload()
        }
        //camv.loadUrl("http://baidu.com")


        val eUrl = binding.editTextTextPersonName
        eUrl.text.clear()
        eUrl.text.appendLine("http://192.168.1.254:8192")

        val btnReload = binding.btnReload

        btnReload.setOnClickListener {
            camv.loadUrl(eUrl.toString())
            camv.reload()
        }

        val btnPhoto = binding.btnPhoto

        btnPhoto.setOnClickListener {
            CamCommand("1001", "0")
        }

        val btnRecordStart = binding.btnRecordStart

        btnRecordStart.setOnClickListener {
            CamCommand("2001", "1")
        }

        val btnRecordStop = binding.btnRecordStop

        btnRecordStop.setOnClickListener {
            CamCommand("2001", "0")
        }

        val camSwitch = binding.camSwitch

        camSwitch.setOnClickListener {

            //  camSwitch.isChecked=!camSwitch.isChecked
            if (camSwitch.isChecked) {
                CamCommand("3001", "1")
                camSwitch.text = "Record Mode"
            } else {
                CamCommand("3001", "0")
                camSwitch.text = "Photo Mode"
            }

        }
    }

    open fun CamCommand(cmd: String, opt: String) {

        GlobalScope.launch {

            val url: Uri.Builder = Uri.Builder()
            url.scheme("http")
                .authority("192.168.1.254")
                .appendQueryParameter("custom", "1")
                .appendQueryParameter("cmd", cmd)
                .appendQueryParameter("par", opt)
            println(url.toString())
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url.toString())
                .build()
            println(request.body.toString())
            try {
                val response = client.newCall(request).execute()
                println(response.body.toString())
            } catch (e: IOException) {
            }


        }
    }


}