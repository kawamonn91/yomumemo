package jp.yomumemo.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFailureMappingTest {
    @Test
    fun `カメラ権限がブロックされている場合は専用の状態になる`() {
        val state = scanFailureToUiState(BarcodeScanFailure.CameraPermissionBlocked)
        assertEquals(ScanUiState.CameraPermissionBlocked, state)
    }

    @Test
    fun `スキャナが利用できない場合はGoogle Play開発者サービスへの言及を含むヒントになる`() {
        val state = scanFailureToUiState(BarcodeScanFailure.ScannerUnavailable)
        assertTrue(state is ScanUiState.Choose)
        assertTrue((state as ScanUiState.Choose).hint!!.contains("Google Play 開発者サービス"))
    }

    @Test
    fun `原因不明の場合は明るさとISBN手入力を促す一般的なヒントになる`() {
        val state = scanFailureToUiState(BarcodeScanFailure.Unknown)
        assertTrue(state is ScanUiState.Choose)
        assertTrue((state as ScanUiState.Choose).hint!!.contains("ISBN"))
    }

    @Test
    fun `異なる失敗理由は異なるヒントになる`() {
        val unavailable = scanFailureToUiState(BarcodeScanFailure.ScannerUnavailable) as ScanUiState.Choose
        val unknown = scanFailureToUiState(BarcodeScanFailure.Unknown) as ScanUiState.Choose
        assertTrue(unavailable.hint != unknown.hint)
    }
}
