# ヨムメモ (YomuMemo)

バーコードで本を登録し、読みながら気づいたことをその場で書き留める Android アプリ。

## 状態

アプリは完成しており、署名済みの AAB を生成できる。Play Console での公開作業が残っている。
手順は [docs/play-console-checklist.md](docs/play-console-checklist.md) を参照。

公開は**既に持っている個人アカウント**で行う。組織アカウントも検討したが、
D-U-N-S 番号の取得で行き詰まったことと、その利点だった「屋号表示」が
本名公開を受け入れる判断により不要になったため取りやめた。
経緯は [docs/duns-and-organization-account.md](docs/duns-and-organization-account.md)。

**ストアには本名と住所が表示される**（了承済み）。
アカウントは2023年11月13日以降の作成のため、製品版公開前に
**「12人のテスター × 連続14日間」のクローズドテストが必要**
（Play Console のダッシュボードで確認済み）。
内部テストは要件と無関係にいつでも使えるので、実機確認は先に進められる。

決済は Google Play Billing を使う。代替課金（Stripe 等）は日本でも解禁されたが、
サービス手数料 10% は決済手段を問わず発生し、浮くのは決済手数料だけ。
そこに Stripe の 3.6% が乗るため実質差は 1.4% 程度で、実装と運用の負担に見合わない。

```
単体テスト  128 件
計装テスト   24 件（実機/エミュレータ上）
```

## ビルド

```bash
./gradlew :app:assembleDebug      # デバッグ版 APK
./gradlew :app:bundleRelease      # 署名済み AAB（Play へのアップロード用）
./gradlew :app:testDebugUnitTest  # 単体テスト
./gradlew :app:connectedDebugAndroidTest  # 計装テスト（端末が必要）
```

`local.properties` に SDK の場所を書く。任意で表紙取得用のキーを足せる。

```properties
sdk.dir=C:/Users/<ユーザー名>/AppData/Local/Android/Sdk
googleBooksApiKey=AIza...
```

キーが無くてもビルドも動作もする（表紙がプレースホルダになる）。
取得手順は [docs/google-books-api-key.md](docs/google-books-api-key.md)。

## 構成

| 層 | 中身 |
|---|---|
| `data/db` | Room。books / notes / notes_fts(FTS4) / tags / note_tags |
| `data/remote` | openBD と Google Books からの書誌取得 |
| `data/repo` | 本とメモの操作 |
| `billing` | Play Billing と機能ゲート |
| `sync` | バックアップ用スナップショットと統合 |
| `export` | Markdown / CSV / Obsidian 書き出し |
| `ocr` | 引用のカメラ取り込み |
| `ui` | Compose の画面 |

## 設計上の判断

**ID は端末生成の UUID。** 自動採番だと、複数端末が同じ番号を別のレコードに
割り当ててしまい、バックアップの統合時に取り違える。

**削除は論理削除。** 行を消すと「相手が消した」のか「相手がまだ知らない」のか
区別できず、消したはずの本が復活する。

**書誌は openBD を第一情報源にする。** 和書の書名・著者・出版社が最も正確で、
APIキー不要・利用制限なしのため全ユーザーで確実に動く。
ただし実測（無作為1,000件）では表紙を約6%、ページ数を約10%、内容紹介を約16%
しか持たないため、これらは Google Books で補う。

**日本語の検索は LIKE、英数字は FTS。** SQLite の simple トークナイザは
ASCII の英数字しか語の区切りを知らないため、日本語の連なりは「まるごと1語」として
索引される。FTS では文頭からの前方一致しか成立せず、語の途中は引けない
（実機テストで確認済み）。

**機能ゲートは `EntitlementRepository` に集約。** 画面もビューモデルも
Play Billing を直接見ない。将来サブスクを追加する場合も、購入状態の求め方を
ここで変えるだけで済む。

**バーコードは Google code scanner。** Play 開発者サービスがスキャン画面ごと
提供するためカメラ権限が不要で、初回登録の離脱を減らせる。
カメラ権限は引用のカメラ取り込みでのみ求める。

## 日本の書籍バーコードについて

日本の本には上下2段のバーコードが印刷されている。

- 上段 `978…` — ISBN。書誌検索に使う
- 下段 `192…` — 日本図書コードの価格情報。本の特定には使えない

下段を読んでしまう誤操作が非常に多いため、検出して上段を読むよう案内している。

## 開発用の道具

- `tools/drive-emulator.sh` — エミュレータ操作の補助。Compose のボタンは
  uiautomator 上で text が空になるため、文字列ではなく bounds で指す必要がある
- `tools/make_store_assets.py` — ストア用アイコンとフィーチャーグラフィックの生成

## 既知の制約

- **バーコードの実読み取りと OCR は実機でのみ検証できる。**
  エミュレータの疑似カメラでは本物のバーコードもページも写せない
- **課金の動作確認は内部テストトラック経由でのみ行える。**
  ローカルにインストールした APK では Play Billing が動かない
- Google ドライブ同期は未実装。スナップショットと統合処理は実装済みなので、
  保管場所を Drive に差し替える形で追加できる
