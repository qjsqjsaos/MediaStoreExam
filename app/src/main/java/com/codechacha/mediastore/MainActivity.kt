package com.codechacha.mediastore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val READ_EXTERNAL_STORAGE_REQUEST = 0x1045
        const val TAG = "MainActivity"
    }

    private val images = MutableLiveData<List<MediaStoreImage>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val galleryAdapter = GalleryAdapter()
        gallery.also { view ->
            view.layoutManager = GridLayoutManager(this, 3)
            view.adapter = galleryAdapter
        }

        galleryAdapter.setOnItemClickListener(object: GalleryAdapter.OnItemClickListener {
            override fun onItemClick(mediaStoreImage: MediaStoreImage) {

                //위엔꺼는 bitmap 반환
                Glide.with(image)
                    .asBitmap()
                    .load(mediaStoreImage.contentUri)
                    .into(object : CustomTarget<Bitmap>(){
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            image.setImageBitmap(resource)
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {
                            // this is called when imageView is cleared on lifecycle call or for
                            // some other reason.
                            // if you are referencing the bitmap somewhere else too other than this imageView
                            // clear it here as you can no longer have the bitmap
                        }
                    })

                //아래꺼는 기본

//                Glide.with(image)
//                    .load(mediaStoreImage.contentUri)
//                    .centerCrop()
//                    .into(image)
            }

        })

//        //이미지 겹치기 연습중
//        button.setOnClickListener{
//
//            val resources: Resources = this.resources
//            val bitmap1 = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
//
//
//            val resources2: Resources = this.resources
//            val bitmap2 = BitmapFactory.decodeResource(resources2, R.drawable.ic_launcher_background)
//
//            val resources3: Resources = this.resources
//            val drawable = BitmapDrawable(resources3, ImageUtil.overlay(bitmap1, bitmap2))
//            result.setImageDrawable(drawable)
//
//        }

        images.observe(this, Observer<List<MediaStoreImage>> { images ->
            galleryAdapter.submitList(images)
        })

        openMediaStore()


    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_EXTERNAL_STORAGE_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    showImages()
                } else {
                    val showRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )

                    if (!showRationale) {
                        goToSettings()
                    }
                }
                return
            }
        }
    }

    private fun showImages() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val albumStr = getAlbumStr()
            for (i: String in albumStr){
                Log.d("앨범이름", i)
            }

            val imageList = queryImages("Camera")
            images.postValue(imageList)
        }
    }

    private fun openMediaStore() {
        if (haveStoragePermission()) {
            showImages()
        } else {
            requestPermission()
        }
    }

    private fun goToSettings() {
        Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            startActivity(intent)
        }
    }

    private fun haveStoragePermission() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PERMISSION_GRANTED

    private fun requestPermission() {
        if (!haveStoragePermission()) {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions, READ_EXTERNAL_STORAGE_REQUEST)
        }
    }

    //앨범들 String 데이터 가져오기
    //중복된 값이 없어야하기 때문에, Set으로 설정하였다.
    private suspend fun getAlbumStr(): MutableSet<String> {
        val albums = mutableSetOf<String>()

        withContext(Dispatchers.IO) {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else {
                arrayOf(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, // selection
                null, // selectionArgs
                sortOrder
            )?.use { cursor ->
                val bucketDisplayColumn =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    }

                while (cursor.moveToNext()) {
                    val album = cursor.getString(bucketDisplayColumn)
                    albums += album
                }
            }
        }
        return albums
    }

    private suspend fun queryImages(selectionArg: String?): List<MediaStoreImage> {
        val images = mutableListOf<MediaStoreImage>()

        withContext(Dispatchers.IO) {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
                )
            }

            //조건절에 해당한다.
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Audio.Media.BUCKET_DISPLAY_NAME} == ?"
            } else {
                "${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME} == ?"
            }

            //조건절에 ?에 해당하는 값이다. 배열값으로 넣어준다. ex) "Camera"
            val selectionArgs = arrayOf(
                selectionArg
            )

            //정렬값을 만들어준다. 오름 내림 차순 ASC / DESC
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection, // selection
                selectionArgs, // selectionArgs
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateTakenColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketDisplayColumn =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateTaken = Date(cursor.getLong(dateTakenColumn))
                    val displayName = cursor.getString(displayNameColumn)
                    val bucketDisplayName = cursor.getString(bucketDisplayColumn)
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val image = MediaStoreImage(id, displayName, dateTaken, contentUri, bucketDisplayName)
                    images += image
                    Log.d(TAG, image.toString())
                }
                cursor.close()
            }
        }


        for (i: Int in 0 until images.size){
            Log.d("뭘까?", images[i].bucketDisplayName);
        }

        Log.d(TAG, "Found ${images.size} images")
        return images
    }

//    @SuppressLint("SimpleDateFormat")
//    private fun dateToTimestamp(day: Int, month: Int, year: Int): Long =
//        SimpleDateFormat("dd.MM.yyyy").let { formatter ->
//            formatter.parse("$day.$month.$year")?.time ?: 0
//        }

}
