@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.mobile_dev_project

import android.Manifest
import android.content.ContentUris
import androidx.compose.material3.TextFieldDefaults

import android.content.ContentValues
import androidx.compose.material3.OutlinedTextFieldDefaults

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// --- Palette sombre personnalisée ---
private val Purple80 = Color(0xFFBB86FC)
private val Teal200  = Color(0xFF03DAC5)

private val DarkColors = darkColorScheme(
    primary    = Purple80,
    onPrimary  = Color.White,
    secondary  = Teal200,
    background = Color(0xFF121212),
    surface    = Color(0xFF1E1E1E),
    onSurface  = Color.White
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_CONTACTS     = 1001
        private const val REQUEST_CODE_CAMERA_PERMS = 2001
        private val CAMERA_PERMS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private lateinit var cameraExecutor: ExecutorService
    private val useCameraState = mutableStateOf(true)
    private val pendingContact = mutableStateOf<ContactData?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Demande des permissions caméra dès le lancement
        ActivityCompat.requestPermissions(
            this,
            CAMERA_PERMS,
            REQUEST_CODE_CAMERA_PERMS
        )
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme(
                colorScheme = DarkColors,
                typography  = Typography()
            ) {
                AppContent()
            }
        }
    }

    @Composable
    private fun AppContent() {
        val context        = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // États partagés
        var bmp      by remember { mutableStateOf<Bitmap?>(null) }
        var status   by remember { mutableStateOf("") }
        var showForm by remember { mutableStateOf(false) }
        val useCamera by useCameraState

        // Champs du formulaire
        var firstName by remember { mutableStateOf("") }
        var lastName  by remember { mutableStateOf("") }
        var phone     by remember { mutableStateOf("") }
        var email     by remember { mutableStateOf("") }

        // ImageCapture partagé
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

        // Launcher pour galerie
        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val image = BitmapFactory.decodeStream(stream)
                    bmp = image
                    status = "Reconnaissance en cours…"
                    runOcr(image) { fn, ln, ph, em ->
                        firstName = fn; lastName = ln; phone = ph; email = em
                        showForm = true
                    }
                }
            }
        }

        if (!showForm) {
            // Écran Caméra / Galerie
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("OCR + CameraX") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor    = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Preview Caméra
                    if (useCamera) {
                        val providerFuture = remember {
                            ProcessCameraProvider.getInstance(context)
                        }
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).also { previewView ->
                                    imageCapture = ImageCapture.Builder().build()
                                    val preview = Preview.Builder()
                                        .build()
                                        .apply {
                                            setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                    providerFuture.get().bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Affichage image capturée / galerie
                    bmp?.let {
                        Image(
                            bitmap           = it.asImageBitmap(),
                            contentDescription = null,
                            modifier         = Modifier.fillMaxSize(),
                            contentScale     = ContentScale.Crop
                        )
                    }

                    // Boutons en bas
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("Galerie") }

                        Button(
                            onClick = {
                                imageCapture?.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(proxy: ImageProxy) {
                                            val bmpCaptured = proxy.toBitmap()
                                            bmp = bmpCaptured
                                            useCameraState.value = false
                                            status = "Reconnaissance en cours…"
                                            runOcr(bmpCaptured) { fn, ln, ph, em ->
                                                firstName = fn; lastName = ln
                                                phone     = ph; email = em
                                                showForm = true
                                            }
                                        }
                                        override fun onError(exc: ImageCaptureException) {
                                            status = "Erreur capture : ${exc.message}"
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor   = MaterialTheme.colorScheme.onSecondary
                            )
                        ) { Text("Prendre photo") }
                    }
                }
            }

        } else {
            // Écran Formulaire Complet
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Valider le contact") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor    = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Boucle sur nos 4 champs via Triple(label, value, setter)
                    listOf(
                        Triple("Prénom",    firstName) { v: String -> firstName = v },
                        Triple("Nom",       lastName)  { v: String -> lastName  = v },
                        Triple("Téléphone", phone)     { v: String -> phone     = v },
                        Triple("Email",     email)     { v: String -> email     = v }
                    ).forEach { (label, valueToShow, onChange) ->
                        OutlinedTextField(
                            value = valueToShow,
                            onValueChange = onChange,
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (label) {
                                    "Téléphone" -> KeyboardType.Phone
                                    "Email"     -> KeyboardType.Email
                                    else        -> KeyboardType.Text
                                },
                                imeAction = if (label == "Email") ImeAction.Done else ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor     = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                cursorColor            = MaterialTheme.colorScheme.primary,
                                focusedLabelColor      = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor    = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            pendingContact.value = ContactData(
                                firstName, lastName, phone, email
                            )
                            saveContact(pendingContact.value!!)
                            Toast
                                .makeText(context, "Contact ajouté", Toast.LENGTH_SHORT)
                                .show()
                            showForm = false
                        },
                        Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }

    // OCR + Regex
    private fun runOcr(
        bmp: Bitmap,
        onResult: (String, String, String, String) -> Unit
    ) {
        TextRecognition
            .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { visionText ->
                val raw   = visionText.text
                val email = Regex("[\\w._%+-]+@[\\w.-]+\\.[a-z]{2,}")
                    .find(raw)?.value.orEmpty()
                val phone = Regex("\\+?\\d[\\d \\-]{7,}\\d")
                    .find(raw)?.value?.replace("[ \\-]", "").orEmpty()
                val nameLine = raw.lines()
                    .firstOrNull {
                        it.isNotBlank() &&
                                !it.contains(email) &&
                                !it.contains(phone)
                    }.orEmpty()
                val parts = nameLine.split(" ", limit = 2)
                onResult(
                    parts.getOrNull(0).orEmpty(),
                    parts.getOrNull(1).orEmpty(),
                    phone, email
                )
            }
            .addOnFailureListener { /* ignore */ }
    }

    // Conversion ImageProxy → Bitmap
    private fun ImageProxy.toBitmap(): Bitmap {
        val bitmap = when (format) {
            ImageFormat.JPEG -> {
                val buffer = planes.first().buffer
                val bytes  = ByteArray(buffer.remaining()).also(buffer::get)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            ImageFormat.YUV_420_888 -> {
                val y = planes[0].buffer
                val u = planes[1].buffer
                val v = planes[2].buffer
                val nv21 = ByteArray(y.remaining() + u.remaining() + v.remaining())
                y.get(nv21, 0, y.remaining())
                v.get(nv21, y.remaining(), v.remaining())
                u.get(nv21, y.remaining() + v.remaining(), u.remaining())
                val yuv = android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    width, height, null
                )
                ByteArrayOutputStream().use { out ->
                    yuv.compressToJpeg(
                        android.graphics.Rect(0,0,width,height),
                        100, out
                    )
                    BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                }
            }
            else -> throw IllegalArgumentException("Format non supporté : $format")
        }
        close()
        return bitmap
    }

    // Enregistrement du contact
    private fun saveContact(contact: ContactData) {
        try {
            val cv = ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            }
            val rawUri = contentResolver.insert(
                ContactsContract.RawContacts.CONTENT_URI, cv
            )!!
            val rawId  = ContentUris.parseId(rawUri)

            fun insert(mime: String, field: Pair<String,String>) {
                cv.clear()
                cv.put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                cv.put(ContactsContract.Data.MIMETYPE, mime)
                cv.put(field.first, field.second)
                contentResolver.insert(ContactsContract.Data.CONTENT_URI, cv)
            }

            insert(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME to contact.firstName
            )
            insert(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME to contact.lastName
            )
            insert(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER to contact.phone
            )
            insert(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.ADDRESS to contact.email
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur ajout contact", Toast.LENGTH_SHORT).show()
        }
    }

    data class ContactData(
        val firstName: String,
        val lastName: String,
        val phone: String,
        val email: String
    )

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
