package com.bedirhanyilmazs.artbookkotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.ScriptGroup.Binding
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bedirhanyilmazs.artbookkotlin.databinding.ActivityArtBinding
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayOutputStream
import kotlin.math.max

class ArtActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtBinding
    // galeriye gitmek için kullanıyoruz
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // imageleri bitmap yapıp kücültmek
    private var selectedBitmap : Bitmap? = null

    private lateinit var database : SQLiteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", MODE_PRIVATE, null)

        registerLauncher()

        val intent = intent
        val info = intent.getStringExtra("info")
        if(info.equals("new")) {
            binding.artNameText.setText("")
            binding.artistNameText.setText("")
            binding.yearsText.setText("")
            binding.button.visibility = View.VISIBLE
            binding.imageView.setImageResource(R.drawable.selectimage)
            binding.deleteButton.visibility = View.INVISIBLE

        }else {
            binding.button.visibility = View.INVISIBLE
            binding.deleteButton.visibility = View.VISIBLE
            val selectedId = intent.getIntExtra("id",1)

            val cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", arrayOf(selectedId.toString()))

            val artNameIx = cursor.getColumnIndex("artname")
            val artistNameIx = cursor.getColumnIndex("artistname")
            val yearIx = cursor.getColumnIndex("year")
            val imageIx = cursor.getColumnIndex("image")

            while(cursor.moveToNext()) {
                binding.artNameText.setText(cursor.getString(artNameIx))
                binding.artistNameText.setText(cursor.getString(artistNameIx))
                binding.yearsText.setText(cursor.getString(yearIx))

                val byteArray = cursor.getBlob(imageIx)
                val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.imageView.setImageBitmap(bitmap)
            }

            cursor.close()

            // delete icin
            binding.deleteButton.setOnClickListener {
                deleteButtonClicked(it)
            }
        }
    }


        fun saveButtonClicked(view : View) {

            val artName = binding.artNameText.text.toString()
            val artistName = binding.artistNameText.text.toString()
            val year = binding.yearsText.text.toString()

            if(selectedBitmap != null) {
                val smallBitmap = makeSmallerBitmap(selectedBitmap!!,300)

                val outputStream = ByteArrayOutputStream()
                smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
                val byteArray = outputStream.toByteArray()

                try {
                    val database = this.openOrCreateDatabase("Arts", MODE_PRIVATE,null)
                    database.execSQL("CREATE TABLE IF NOT EXISTS arts (id INTEGER PRIMARY KEY, artname VARCHAR, artistname VARCHAR, year VARCHAR, image BLOB)")

                    val sqlString = "INSERT INTO arts (artname, artistname, year , image) VALUES (?,?,?,?)"
                    val statement = database.compileStatement(sqlString)
                    statement.bindString(1,artName)
                    statement.bindString(2,artistName)
                    statement.bindString(3,year)
                    statement.bindBlob(4,byteArray)
                    statement.execute()

                } catch(e: Exception) {
                    e.printStackTrace()
                }

                val intent = Intent(this@ArtActivity,MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
        }

    private fun deleteButtonClicked(view: View) {
        val selectedId = intent.getIntExtra("id",-1)

        if(selectedId != -1) {
            try {
                // veritabanı bağlantısı
                val statement = database.compileStatement("DELETE FROM arts WHERE id = ?")
                statement.bindLong(1, selectedId.toLong())
                statement.executeUpdateDelete()

                Toast.makeText(this,"Item deleted!",Toast.LENGTH_SHORT).show()

                // MainActivity'e geri dön
                val intent = Intent(this@ArtActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else{
            Toast.makeText(this,"Error deleting item!", Toast.LENGTH_SHORT).show()
        }
    }


        private fun makeSmallerBitmap(image : Bitmap, maximumSize: Int) : Bitmap {
            var width = image.width
            var height = image.height

            val bitmapRatio : Double = width.toDouble() / height.toDouble()

            if(bitmapRatio > 1) {
                // landscape
                width = maximumSize
                val scaleHeight = width / bitmapRatio
                height = scaleHeight.toInt()
            } else{
                // portrait
                height = maximumSize
                val scaledWidth = height * bitmapRatio
                width = scaledWidth.toInt()
            }

            return Bitmap.createScaledBitmap(image,width,height,true)
        }

        fun selectImage(view : View) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 33+ -> READ_MEDIA_IMAGES
            // izin kontrol etme
            // izin verilmedi
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_MEDIA_IMAGES)){
                    // rationale
                    Snackbar.make(view,"Permission needed for gallery",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission",View.OnClickListener {
                        // request permission
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    }).show()
                }else {
                    //request permission
                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }


            }else { // izin verildi
                val intentToGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                // intent
                activityResultLauncher.launch(intentToGallery)
            }
        } else{  // Android 32- -> READ_EXTERNAL_STORAGE
            // izin kontrol etme
            // izin verilmedi
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)){
                    // rationale
                    Snackbar.make(view,"Permission needed for gallery",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission",View.OnClickListener {
                        // request permission
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }).show()
                }else {
                    //request permission
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }


            }else { // izin verildi
                val intentToGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                // intent
                activityResultLauncher.launch(intentToGallery)
            }
        }


        }

    private fun registerLauncher() {

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if(result.resultCode == RESULT_OK) {
                val intentFromResult = result.data
                if(intentFromResult != null) {
                    val imageData = intentFromResult.data
                    //binding.imageView.setImageURI(imageData)
                    if(imageData != null) {
                        try {
                            if(Build.VERSION.SDK_INT >= 28) {
                                val source = ImageDecoder.createSource(this@ArtActivity.contentResolver,imageData)
                                selectedBitmap = ImageDecoder.decodeBitmap(source)
                                binding.imageView.setImageBitmap(selectedBitmap)
                            } else{
                                selectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver,imageData)
                                binding.imageView.setImageBitmap(selectedBitmap)
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                }
            }
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if(result) {
                // permission granted
                val intentToGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }else {
                // permission denied
                Toast.makeText(this@ArtActivity,"Permission needed!",Toast.LENGTH_LONG).show()
            }
        }


    }
}