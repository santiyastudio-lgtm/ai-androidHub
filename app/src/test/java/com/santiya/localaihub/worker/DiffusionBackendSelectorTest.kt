package com.santiya.localaihub.worker

import com.santiya.localaihub.global.AccelerationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffusionBackendSelectorTest {

    @Test
    fun `gpu mode uses hardware backend on qualcomm when qnn assets exist`() {
        val selection = DiffusionBackendSelector.resolve(
            mode = AccelerationMode.GPU,
            isQualcommDevice = true,
            hasQnnBackend = true,
            hasCpuBackend = true,
            hasNpuClip = true,
            hasCpuClip = true
        )

        assertFalse(selection.runOnCpu)
        assertFalse(selection.useCpuClip)
        assertEquals(AccelerationMode.GPU, selection.effectiveMode)
        assertTrue(selection.hardwareBackendAvailable)
    }

    @Test
    fun `gpu mode falls back to cpu when hardware backend is unavailable`() {
        val selection = DiffusionBackendSelector.resolve(
            mode = AccelerationMode.GPU,
            isQualcommDevice = false,
            hasQnnBackend = true,
            hasCpuBackend = true,
            hasNpuClip = false,
            hasCpuClip = true
        )

        assertTrue(selection.runOnCpu)
        assertTrue(selection.useCpuClip)
        assertEquals(AccelerationMode.CPU, selection.effectiveMode)
        assertFalse(selection.hardwareBackendAvailable)
    }

    @Test
    fun `cpu mode stays on cpu when cpu assets exist`() {
        val selection = DiffusionBackendSelector.resolve(
            mode = AccelerationMode.CPU,
            isQualcommDevice = true,
            hasQnnBackend = true,
            hasCpuBackend = true,
            hasNpuClip = true,
            hasCpuClip = true
        )

        assertTrue(selection.runOnCpu)
        assertTrue(selection.useCpuClip)
        assertEquals(AccelerationMode.CPU, selection.effectiveMode)
    }

    @Test
    fun `cpu mode falls back to hardware when cpu assets are missing`() {
        val selection = DiffusionBackendSelector.resolve(
            mode = AccelerationMode.CPU,
            isQualcommDevice = true,
            hasQnnBackend = true,
            hasCpuBackend = false,
            hasNpuClip = false,
            hasCpuClip = false
        )

        assertFalse(selection.runOnCpu)
        assertFalse(selection.useCpuClip)
        assertEquals(AccelerationMode.GPU, selection.effectiveMode)
        assertTrue(selection.hardwareBackendAvailable)
    }

    @Test
    fun `auto mode uses hardware clip fallback when qnn clip is absent`() {
        val selection = DiffusionBackendSelector.resolve(
            mode = AccelerationMode.AUTO,
            isQualcommDevice = true,
            hasQnnBackend = true,
            hasCpuBackend = true,
            hasNpuClip = false,
            hasCpuClip = true
        )

        assertFalse(selection.runOnCpu)
        assertTrue(selection.useCpuClip)
        assertEquals(AccelerationMode.GPU, selection.effectiveMode)
    }
}
