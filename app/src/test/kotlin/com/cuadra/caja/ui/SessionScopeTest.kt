package com.cuadra.caja.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cuadra.caja.data.session.Session
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ADR 0014: lo que las pantallas recuerdan en memoria (la lista de ventas del servidor, la de negocios de la cuenta…) no sobrevive a cerrar sesión, a entrar
 * con otra cuenta ni a cambiar de negocio, y SÍ sobrevive a cambiar de persona (PIN) en el mismo negocio (la venta en curso no se pierde).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionScopeTest {
    class Memory : ViewModel() { var remembered: String? = null }

    private fun session(user: String? = null, device: String? = null, business: String? = null, member: String? = null) =
        Session(userToken = user, deviceToken = device, deviceId = device?.let { "d-$it" }, businessId = business, memberId = member)

    @Test fun viewModelsLiveAsLongAsTheSessionAndNoLonger() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        val current = mutableStateOf(session(user = "u1", device = "t1", business = "A", member = "m1"))
        val seen = mutableListOf<Memory>()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { it.setContent { SessionScope(current.value) { seen += viewModel<Memory>(factory = viewModelFactory { initializer { Memory() } }) } } }
        fun settle() { org.robolectric.shadows.ShadowLooper.idleMainLooper(); scenario.onActivity { } }
        settle()
        val first = seen.last()
        first.remembered = "ventas de A"

        // Otra persona en el mismo negocio y teléfono: la misma memoria.
        current.value = session(user = "u1", device = "t1", business = "A", member = "m2"); settle()
        assertSame(first, seen.last())

        // Cierra sesión: se descarta.
        current.value = session(); settle()
        val signedOut = seen.last()
        assertNotSame(first, signedOut)
        assertSame(null, signedOut.remembered)

        // Otra cuenta de Google en el onboarding, y luego su negocio nuevo: cada paso empieza de cero.
        signedOut.remembered = "negocios de la cuenta anterior"
        current.value = session(user = "u2"); settle()
        assertNotSame(signedOut, seen.last())
        assertSame(null, seen.last().remembered)
        seen.last().remembered = "x"
        current.value = session(user = "u2", device = "t2", business = "B"); settle()
        assertSame(null, seen.last().remembered)

        // Mismo usuario y teléfono pero OTRO negocio: también.
        seen.last().remembered = "y"
        current.value = session(user = "u2", device = "t2", business = "C"); settle()
        assertSame(null, seen.last().remembered)
        scenario.close()
    }
}
