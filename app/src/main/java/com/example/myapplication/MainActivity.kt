package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


// bien xac dinh ngu canh
private const val REQUEST_CODE_PERMISSIONS=10

//danh sach tat ca permission specified in mainfest

private val REQUEST_PERMISSTION= arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewFinder=findViewById(R.id.view_finder)
        // kiem tra quyeen camera
        if(allPermisstionGranted()){ // neu da co quyen thi start camera
            viewFinder.post { startCamera() }
        }
        else{
            ActivityCompat.requestPermissions(this, // neu chua co quyen thi  yeu cau xac nhan quyen
                REQUEST_PERMISSTION, REQUEST_CODE_PERMISSIONS)
        }
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private  val executor= Executors.newSingleThreadExecutor()
    private lateinit var viewFinder:TextureView

    private  fun startCamera(){
        //tao thiet lap doi tuong cho viewfinder user case
        val previewConfig =PreviewConfig.Builder().apply {
            setTargetResolution(Size(640,480))
        }.build()
        // xau dung viewfinder cho nguoi dung
        val  preview =Preview(previewConfig)
        // tinh toan lai bo cuc khi update

        val imageCaptureConfig =ImageCaptureConfig.Builder().apply {
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            // tuy thich do phan giai
        }.build()

        val imageCapture =ImageCapture(imageCaptureConfig)
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener{
            val file = File(externalMediaDirs.first(),
                "${System.currentTimeMillis()}.jpg")
            imageCapture.takePicture(file, executor,object :ImageCapture.OnImageSavedListener{
                override fun onImageSaved(file: File) {
                   val msg="Photo captuer succeeded :${file.absoluteFile}"
                    Log.d("CameraXApp", msg)
                    viewFinder.post {
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(
                    imageCaptureError: ImageCapture.ImageCaptureError,
                    message: String,
                    cause: Throwable?
                ) {
                    val msg= "Photo capture failed :$message"
                    Log.e("CameraXApp", msg,cause)
                    viewFinder.post {
                        Toast.makeText(baseContext, msg,Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        preview.setOnPreviewOutputUpdateListener{

            //để update được SurfaceTexture , chung ta phai xoa no va add la no
            val parent= viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder,0)
            viewFinder.surfaceTexture=it.surfaceTexture
//            updateTransform()


            //bind user case to lifecycle
            // if android studio complains about " thos" being no a lifecycleOwner
            //try rebuilding the project or updateing the appcompat dependecy tp version 1.1.0 or higher

        }

        val analyzerConfig =ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()
        val analyzerUserCase=ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor,LuminostiyAnalyzer())
        }
        CameraX.bindToLifecycle(this, preview,imageCapture, analyzerUserCase)
    }
    private fun updateTransform(){
        val matrix= Matrix()

        val centerX= viewFinder.width/2f
        val centerY= viewFinder.height/2f

        val rotationDegrees= when(viewFinder.display.rotation){
            Surface.ROTATION_0->0
            Surface.ROTATION_90 ->90
            Surface.ROTATION_180 ->180
            Surface.ROTATION_270 -> 270
            else ->return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        viewFinder.setTransform(matrix)
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(allPermisstionGranted()){
            viewFinder.post { startCamera() }

        }
        else{
            Toast.makeText(this, "Permissions not grandtec by the user", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    // kiem tra tat ca cac quyen duoc cap chua ..
    private fun allPermisstionGranted()= REQUEST_PERMISSTION.all {
        ContextCompat.checkSelfPermission(baseContext, it)==PackageManager.PERMISSION_GRANTED
    }

    private class LuminostiyAnalyzer :ImageAnalysis.Analyzer{
        private var lastAnalyzedTimestamp =0L

        private fun ByteBuffer.toByteArray(): ByteArray{
            rewind()
            val data =ByteArray(remaining())
            get(data)
            return data
        }
        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
            val currencyTimestamp = System.currentTimeMillis()
            if(currencyTimestamp-lastAnalyzedTimestamp>=TimeUnit.SECONDS.toMillis(1)){
                val buffer= image?.planes?.get(0)?.buffer
                val data = buffer?.toByteArray()
                val pixels= data?.map { it.toInt() and 0xFF }

                val luma=pixels?.average()

                Log.d("CameraXApp", "Averager luminostity : $luma")

                lastAnalyzedTimestamp =currencyTimestamp
            }
        }

    }


}
