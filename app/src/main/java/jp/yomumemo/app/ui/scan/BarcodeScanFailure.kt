package jp.yomumemo.app.ui.scan

/**
 * バーコードスキャナ([BarcodeScannerLauncher])が失敗した理由をユーザーに説明できる粒度に分類したもの。
 *
 * Google code scanner の失敗はほとんどが `MlKitException.UNKNOWN` や `INTERNAL` などの
 * 内部的なコードで返ってくるため全てを見分けられるわけではないが、以下の2つは
 * 実際によく起きる・対処法が明確な原因なので個別に扱う価値がある。
 *
 * - [CameraPermissionBlocked]: スキャナ自体は Google Play 開発者サービスの中で動くため、
 *   本アプリはカメラ権限を要求しない。しかし Play 開発者サービス自身のカメラ権限が
 *   OS 側で無効化されている(自動権限リボーク、端末の権限管理アプリ、手動でオフにした 等)と、
 *   スキャナ画面は起動してもカメラ映像が出ないままこのエラーで失敗する。
 * - [ScannerUnavailable]: スキャナ機能を提供するモジュールが端末にまだ無い/使えない状態。
 *   初回はネットワーク経由でモジュールを取得する必要があるため、オフラインだと起きやすい。
 *   Play 開発者サービスが古すぎる場合もここに含める。
 */
sealed interface BarcodeScanFailure {
    data object CameraPermissionBlocked : BarcodeScanFailure
    data object ScannerUnavailable : BarcodeScanFailure
    data object Unknown : BarcodeScanFailure
}
