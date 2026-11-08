@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.tools.AppPermission
import com.inspiredandroid.kai.tools.PermissionController
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PermissionCancellationTest {
    @Test fun stopResetsPendingPermissionAndNextRequestStillWorks() = runTest {
        val controller = PermissionController(AppPermission.CAMERA, permissionCheck = { false }, requestAvailable = { true })
        val first = launch { controller.requestPermission() }
        runCurrent()
        assertTrue(controller.permissionRequested.value)
        first.cancelAndJoin()
        assertFalse(controller.permissionRequested.value)
        val next = async { controller.requestPermission() }
        runCurrent()
        controller.onPermissionResult(true)
        assertTrue(next.await())
        assertFalse(controller.permissionRequested.value)
    }

    @Test fun deniedPermissionDoesNotTurnIntoSuccess() = runTest {
        val controller = PermissionController(AppPermission.CAMERA, permissionCheck = { false }, requestAvailable = { true })
        val answer = async { controller.requestPermission() }
        runCurrent()
        controller.onPermissionResult(false)
        assertFalse(answer.await())
    }
}
