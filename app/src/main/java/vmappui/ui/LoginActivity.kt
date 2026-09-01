package vmappui.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vmcontroltemplate.R
import vmappui.model.Session

class LoginActivity : AppCompatActivity() {

    private lateinit var edtPhone: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtLoginError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtPhone = findViewById(R.id.edtPhone)
        btnLogin = findViewById(R.id.btnLogin)
        txtLoginError = findViewById(R.id.txtLoginError)

        btnLogin.setOnClickListener {
            val phone = edtPhone.text.toString().trim()
            if (phone.isEmpty()) {
                txtLoginError.text = "Please enter a valid phone number"
                txtLoginError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            txtLoginError.visibility = View.GONE
            val session = Session(phone = phone)

            Log.d("LoginActivityTest", "Session created successfully: Phone=${session.phone}, LoginTimeMs=${session.loginTimeMs}")

            val intent = Intent(this, CatalogActivity::class.java).apply {
                putExtra("EXTRA_PHONE", session.phone)
                putExtra("EXTRA_LOGIN_TIME", session.loginTimeMs)
            }
            startActivity(intent)
            finish()
        }
    }
}