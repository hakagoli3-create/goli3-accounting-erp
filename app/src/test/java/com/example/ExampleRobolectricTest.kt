package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppSecurityAndSettingsManager
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `test Screen findByRoute resolves all routes without NullPointerException`() {
        val all = Screen.ALL_SCREENS
        assertEquals(10, all.size)
        all.forEach { screen ->
            org.junit.Assert.assertNotNull("Screen should not be null", screen)
            org.junit.Assert.assertNotNull("Route should not be null", screen.route)
            val found = Screen.findByRoute(screen.route)
            assertEquals(screen, found)
        }
        // Test fallback for null or unknown route
        assertEquals(Screen.Dashboard, Screen.findByRoute(null))
        assertEquals(Screen.Dashboard, Screen.findByRoute(""))
        assertEquals(Screen.Dashboard, Screen.findByRoute("unknown_route"))
    }

    @Test
    fun `test launch MainActivity when logged in`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityManager = AppSecurityAndSettingsManager.getInstance(context)
        securityManager.login("admin", "1234")

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.setup().get()
        org.junit.Assert.assertNotNull(activity)
    }

    @Test
    fun `test default admin login and session persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityManager = AppSecurityAndSettingsManager(context)

        // Invalid credentials test
        val failLogin = securityManager.login("admin", "wrong_pass")
        assertFalse(failLogin)

        // Valid default admin login
        val successLogin = securityManager.login("admin", "1234")
        assertTrue(successLogin)
        assertTrue(securityManager.isLoggedIn.value)
        assertEquals(UserRole.ADMIN, securityManager.currentRole.value)
        assertEquals("admin", securityManager.loggedInUsername.value)

        // Session check: creating a new instance on same context preserves login
        val restoredManager = AppSecurityAndSettingsManager(context)
        assertTrue(restoredManager.isLoggedIn.value)
        assertEquals(UserRole.ADMIN, restoredManager.currentRole.value)

        // Logout
        securityManager.logout()
        assertFalse(securityManager.isLoggedIn.value)
    }

    @Test
    fun `test role based access permissions`() {
        // Admin
        assertTrue(UserRole.ADMIN.canViewProfit)
        assertTrue(UserRole.ADMIN.canDeleteDocuments)
        assertTrue(UserRole.ADMIN.canAccessSettings)
        assertTrue(UserRole.ADMIN.canAccessAccountingLedger)

        // POS User / Seller
        assertFalse(UserRole.POS_USER.canViewProfit)
        assertFalse(UserRole.POS_USER.canDeleteDocuments)
        assertFalse(UserRole.POS_USER.canAccessSettings)
        assertTrue(UserRole.POS_USER.canAccessPos)

        // Visitor
        assertFalse(UserRole.VISITOR.canViewProfit)
        assertFalse(UserRole.VISITOR.canDeleteDocuments)
        assertFalse(UserRole.VISITOR.canAccessSettings)
        assertFalse(UserRole.VISITOR.canAccessPos)
        assertTrue(UserRole.VISITOR.isVisitorOnly)
    }

    @Test
    fun `test database user management and loginWithDb`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.database.AppDatabase.getInstance(context)
        val securityManager = AppSecurityAndSettingsManager(context)

        // Ensure default admin exists
        com.example.data.database.DatabaseSeeder.ensureDefaultUsers(db)
        val admin = db.userDao().getUserByUsername("admin")
        org.junit.Assert.assertNotNull(admin)
        assertEquals("admin", admin?.username)
        assertEquals("1234", admin?.passwordHash)

        // Login with DB as admin
        val (adminSuccess, adminError) = securityManager.loginWithDb("admin", "1234", db)
        assertTrue(adminSuccess)
        org.junit.Assert.assertNull(adminError)
        assertEquals("admin", securityManager.loggedInUsername.value)
        assertEquals(UserRole.ADMIN, securityManager.currentRole.value)

        // Create a new seller user
        val sellerUser = com.example.data.model.UserEntity(
            username = "seller_test",
            passwordHash = "pass123",
            fullName = "علی فروشنده",
            role = UserRole.POS_USER.name,
            isActive = true
        )
        db.userDao().insertUser(sellerUser)

        // Test successful seller login
        val (sellerSuccess, _) = securityManager.loginWithDb("seller_test", "pass123", db)
        assertTrue(sellerSuccess)
        assertEquals(UserRole.POS_USER, securityManager.currentRole.value)

        // Test wrong password
        val (wrongPassSuccess, wrongPassError) = securityManager.loginWithDb("seller_test", "wrong", db)
        assertFalse(wrongPassSuccess)
        org.junit.Assert.assertNotNull(wrongPassError)

        // Deactivate seller account
        val fetchedSeller = db.userDao().getUserByUsername("seller_test")!!
        db.userDao().updateUserStatus(fetchedSeller.id, false)

        // Test login with inactive account
        val (inactiveSuccess, inactiveError) = securityManager.loginWithDb("seller_test", "pass123", db)
        assertFalse(inactiveSuccess)
        assertTrue(inactiveError?.contains("غیرفعال") == true)

        // Update password
        db.userDao().updatePassword(fetchedSeller.id, "newpass456")
        db.userDao().updateUserStatus(fetchedSeller.id, true)
        val (newPassSuccess, _) = securityManager.loginWithDb("seller_test", "newpass456", db)
        assertTrue(newPassSuccess)

        // Cleanup
        db.userDao().deleteUserById(fetchedSeller.id)
        org.junit.Assert.assertNull(db.userDao().getUserByUsername("seller_test"))
    }

    @Test
    fun `test cold start to login screen and transition to dashboard on login`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityManager = AppSecurityAndSettingsManager.getInstance(context)
        securityManager.logout() // Start in unauthenticated state
        assertFalse(securityManager.isLoggedIn.value)

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.setup().get()
        org.junit.Assert.assertNotNull(activity)

        // Now perform successful login
        val success = securityManager.login("admin", "1234")
        assertTrue(success)
        assertTrue(securityManager.isLoggedIn.value)
        assertEquals(UserRole.ADMIN, securityManager.currentRole.value)

        // Verify finding routes for dashboard and other screens after login
        val currentScreen = Screen.findByRoute("dashboard")
        assertEquals(Screen.Dashboard, currentScreen)
        assertEquals(10, Screen.ALL_SCREENS.size)
    }
}
