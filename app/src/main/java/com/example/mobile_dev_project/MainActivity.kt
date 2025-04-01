package com.example.mobile_dev_project

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mobile_dev_project.ui.theme.MobiledevprojectTheme
import android.Manifest
import android.widget.Toast
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.widget.Button

class MainActivity : ComponentActivity() {
    private val REQUEST_CODE = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val addContactButton: Button = findViewById(R.id.addContactButton)
        addContactButton.setOnClickListener {
            checkPermissions()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS),
                REQUEST_CODE
            )
        } else {
            saveContact("Enzo", "CHEVALIER", "0654321987", "enzochevalier@gmail.com", "enzochevalier.fr") // Exemple d'enregistrement
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveContact("Dupont", "Jean", "0654321987", "jean.dupont@gmail.com", "jeandupont.fr")
        } else {
            Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveContact(firstName: String, lastName: String, phone: String, email: String, siteWeb: String?) {
        try {
            val contentValues = ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            }

            val rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, contentValues)
            val rawContactId = ContentUris.parseId(rawContactUri!!)

            // Ajouter le prénom et le nom
            contentValues.clear()
            contentValues.apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues)

            // Ajouter le mail
            contentValues.clear()
            contentValues.apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.ADDRESS)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues)

            // Ajouter le site web
            if (siteWeb != null){
                contentValues.clear()
                contentValues.apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Website.URL, siteWeb)
                    put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE)
                }
                contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues)
            }

            // Ajouter le numéro de téléphone
            contentValues.clear()
            contentValues.apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues)

            Toast.makeText(this, "Contact ajouté : $firstName $lastName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur lors de l'ajout du contact", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MobiledevprojectTheme {
        Greeting("Android")
    }
}