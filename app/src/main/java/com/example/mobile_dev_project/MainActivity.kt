package com.example.mobile_dev_project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current


            var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }


            var resultText by remember { mutableStateOf("Aucune image sélectionnée") }


            val labels = remember {
                context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }
            }


            val classifier = remember {
                ImageClassifier.createFromFile(context, "efficientnet-lite4-fp32.tflite")
            }


            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    selectedBitmap = bitmap

                    val image = TensorImage.fromBitmap(bitmap)
                    val results = classifier.classify(image)

                    if (results.isNotEmpty() && results[0].categories.isNotEmpty()) {
                        val category = results[0].categories[0]
                        val label = labels.getOrNull(category.index) ?: "Inconnu"
                        val confidence = (category.score * 100).toInt()
                        resultText = "Objet : $label ($confidence%)"
                    } else {
                        resultText = "Aucun objet reconnu."
                    }
                }
            }


            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("EfficientNet Image Classifier") })
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sélectionner une image")
                    }

                    selectedBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = resultText,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
