package com.salah.upload

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.erikagtierrez.multiple_media_picker.Gallery
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    // Request code
    companion object {
        private const val IMAGE_PERMISSION: Int = 103

        private const val OPEN_MEDIA_PICKER = 1
    }

    private val images = mutableListOf<String>()
    private var adapter: MainActivity.ImageAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupRecyclerView()
        button.setOnClickListener {
            requestPermissions()
        }
    }

    private fun setupRecyclerView() {
        adapter = ImageAdapter()
        adapter?.images = images
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun requestPermissions() {
        val arr = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arr, IMAGE_PERMISSION)
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(this, Gallery::class.java)
        // Set the title
        intent.putExtra("title", "Select media")
        // Mode 1 for both images and videos selection, 2 for images only and 3 for videos!
        intent.putExtra("mode", 2)
        intent.putExtra("maxSelection", 3) // Optional

        startActivityForResult(intent, OPEN_MEDIA_PICKER)
    }

    private fun sendToServer() {

        val files = mutableListOf<File>()
        for (image in images) {
            files.add(File(image))
        }


        val reference = WeakReference<Context>(this)

        ImageAsyncTask(context = reference, images = files, success = { result: JSONObject ->
            if (reference.get() != null) {
                Toast.makeText(reference.get(), "success $result", Toast.LENGTH_LONG).show()
            }
            Log.d(MainActivity::class.java.simpleName, result.toString())
        }, failure = { error ->
            if (reference.get() != null) {
                runOnUiThread {
                    Toast.makeText(reference.get(), error, Toast.LENGTH_LONG).show()
                }
            }
        }).execute("")

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == IMAGE_PERMISSION) {
            for (grantResult in grantResults) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                    break
                }
            }
            Toast.makeText(this, "You must give me Permission to pick images", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OPEN_MEDIA_PICKER) {
            // Make sure the request was successful
            if (resultCode == Activity.RESULT_OK && data != null) {
                val selectionResult = data.getStringArrayListExtra("result")
                images.addAll(selectionResult)
                adapter?.notifyDataSetChanged()
                sendToServer()
            }
        }
    }


    class ImageAdapter : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

        var images: List<String>? = null

        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ImageViewHolder = ImageViewHolder.create(parent = p0)

        override fun getItemCount(): Int = images?.size ?: 0

        override fun onBindViewHolder(p0: ImageViewHolder, p1: Int) = p0.bindTo(image = images?.get(p1))

        class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            companion object {
                fun create(parent: ViewGroup): ImageViewHolder {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
                    return ImageViewHolder(view)
                }
            }


            private val imageView = itemView.findViewById<ImageView>(R.id.imageView)

            fun bindTo(image: String?) {
                val mImage = image.let { it } ?: return
                Glide.with(imageView.context).load(mImage).into(imageView)
            }
        }
    }

    @Suppress("DEPRECATION")
    internal class ImageAsyncTask(
        private val context: WeakReference<Context>,
        private val images: List<File>? = null,
        private val success: (result: JSONObject) -> Unit,
        private val failure: (error: String?) -> Unit
    ) : AsyncTask<String, Int, JSONObject?>() {

        private var exception: Exception? = null

        private lateinit var progressDialog: ProgressDialog

        override fun onPreExecute() {
            super.onPreExecute()
            progressDialog = ProgressDialog(context.get())
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.setCancelable(false)
            progressDialog.setMessage("uploading")
            progressDialog.show()
        }


        override fun doInBackground(vararg params: String?): JSONObject? {
            val url = ""

            val token = ""
            val client = OkHttpClient().newBuilder()
                .connectTimeout(520, TimeUnit.SECONDS)
                .readTimeout(520, TimeUnit.SECONDS)
                .build()

            val builder = MultipartBody.Builder()
            builder.setType(MultipartBody.FORM)

            if (images != null) {
                for ((index, image) in images.withIndex()) {
                    builder.addFormDataPart("images[$index]",
                        image.absolutePath.substring(image.absolutePath.lastIndexOf("/") + 1),
                        CountingFileRequestBody(image, getMimeType(image.path)) { num ->
                            val progress = num / image.length().toFloat() * 100
                            publishProgress(progress.toInt())
                        }
                    )
                }
            }



            builder.addFormDataPart("real_estate_id", "2")
            builder.addFormDataPart("longitude", "34.4673955")
            builder.addFormDataPart("latitude", "31.5051781")
            builder.addFormDataPart("area", "100")
            builder.addFormDataPart("price", "20000")
            builder.addFormDataPart("description", "عقار جنوبي شرقي يحتوي على العديد من المميزات")
            builder.addFormDataPart("city_id", "2")
            builder.addFormDataPart("property_1", "1")
            builder.addFormDataPart("property_2", "1")
            //   builder.addFormDataPart("property_32", "1")

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Accept", "ar")
                .addHeader("Content-Type", "application/json")
                .addHeader("Content-Type", "multipart/form-data")
                .addHeader("Authorization", "Bearer $token")
                .post(builder.build())
                .build()

            var jsonObject: JSONObject? = null
            try {
                client.newCall(request).execute().use { response ->
                    try {
                        if (response.body() != null) {
                            jsonObject = JSONObject(response.body()!!.string())
                            Log.d(ImageAsyncTask::class.java.simpleName, jsonObject!!.toString())
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        Log.e(ImageAsyncTask::class.java.simpleName, e.toString())
                        exception = e
                    }

                    if (!response.isSuccessful) {
                        failure(response.message())
                    }
                }
            } catch (e: IOException) {
                exception = e
                e.printStackTrace()
            }
            return jsonObject
        }

        override fun onPostExecute(jsonObject: JSONObject?) {
            super.onPostExecute(jsonObject)
            progressDialog.dismiss()
            if (jsonObject != null) {
                try {
                    success(jsonObject)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    failure(e.localizedMessage)
                }

            } else {
                if (exception != null) {
                    failure(exception?.localizedMessage)
                } else {
                    failure("Unknowing Error")

                }
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            super.onProgressUpdate(*values)
            progressDialog.progress = values[0] ?: 1
            if (values[0] == 100) {
                progressDialog.dismiss()
                progressDialog = ProgressDialog(context.get())
                progressDialog.setCancelable(false)
                progressDialog.setMessage("Loading")
                progressDialog.show()
            }
        }


        private fun getMimeType(path: String): String? {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path))
        }


    }

}

