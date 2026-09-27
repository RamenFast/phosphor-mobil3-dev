package dev.phosphor.mobil3.ui

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Skip production startup migrations. Tests own only in-memory state and a blank activity. */
class TransposeTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
