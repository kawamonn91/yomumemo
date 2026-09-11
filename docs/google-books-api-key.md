# Google Books API キーの取得と設定

表紙画像・ページ数・内容紹介を補完するために使う。**キーが無くてもアプリはビルドでき、
動作もする**(openBD だけで書誌は引ける)。その場合、表紙はプレースホルダ表示になる。

## なぜキーが必要か

openBD は和書の書誌情報が極めて正確で、APIキー不要・利用制限なしという利点がある。
しかし実測(無作為抽出した1,000冊)では、以下しか持っていない。

| 項目 | openBD の保有率 |
|---|---|
| 表紙画像 | **約 6%** |
| ページ数 | 約 10% |
| 内容紹介 | 約 16% |

2020年以降の新しい本に絞っても表紙は 5.7% で、傾向は変わらなかった。
本棚をグリッド表示するアプリで9割以上の表紙が欠けるのは体裁が悪いため、別系統で補う。

Google Books API は**キー無しでも呼べる形**になっているが、その場合は Google の
共有匿名プロジェクトの割り当てを使うことになり、実際に試したところ既に枯渇していた。

```
Quota exceeded for quota metric 'Queries' and limit 'Queries per day'
of service 'books.googleapis.com' for consumer 'project_number:624717413613'
```

つまり**キー無しの Google Books は使い物にならない**。そのため、キーが未設定のときは
無駄な通信をせず最初から補完を諦める実装にしてある。

## 取得手順

1. [Google Cloud Console](https://console.cloud.google.com/) を開く
2. プロジェクトを新規作成する(名前は `YomuMemo` など任意)
3. **APIとサービス → ライブラリ** で `Books API` を検索し、**有効にする**
4. **APIとサービス → 認証情報 → 認証情報を作成 → APIキー**
5. 作成されたキーの **「キーを制限」** を開き、次の2つを設定する

   **アプリケーションの制限 → Android アプリ**

   パッケージ名と署名証明書の SHA-1 を登録する。
   **デバッグ版とリリース版は別物なので、両方を登録すること**(片方だけだと
   もう片方で 403 になる)。

   | | パッケージ名 | 証明書 |
   |---|---|---|
   | デバッグ | `jp.yomumemo.app.debug` | デバッグ用 keystore の SHA-1 |
   | リリース | `jp.yomumemo.app` | リリース用 keystore の SHA-1 |

   デバッグ用 SHA-1 は次で確認できる。

   ```
   keytool -list -v -alias androiddebugkey \
     -keystore "%USERPROFILE%\.android\debug.keystore" \
     -storepass android -keypass android
   ```

   リリース用は、リリース署名鍵を作成した後に同じ方法で取得して追加する。

   **APIの制限 → キーを制限 → Books API** を選ぶ。
   これで万一キーが漏れても Books API 以外には使えない。

6. `local.properties` に追記する(このファイルは `.gitignore` 済みで、
   リポジトリには入らない)

   ```properties
   googleBooksApiKey=AIza...
   ```

7. 再ビルドする。`BuildConfig.GOOGLE_BOOKS_API_KEY` に値が入る。

## 実装上の注意

キーを「Android アプリ」で制限した場合、リクエストに次の2つのヘッダが必要になる。

```
X-Android-Package: jp.yomumemo.app
X-Android-Cert:    <署名証明書の SHA-1 を大文字16進、コロン無しで>
```

Google のクライアントライブラリはこれを自動で付けるが、本アプリは素の OkHttp で
呼んでいるため自前で付けている(`AndroidAppIdentity`)。**このヘッダが無いと
制限付きキーは 403 を返す**ので、削らないこと。SHA-1 は実行時に自分自身の署名から
算出しているため、デバッグ版とリリース版で自動的に切り替わる。

## 割り当てについて

既定の割り当ては 1日あたり 1,000〜10,000 リクエスト。無料で増枠申請もできる。

本アプリは**取得した書誌を端末のDBに保存する**ため、同じ本について2回目以降は
通信しない。1冊の登録につき生涯1回しか呼ばないので、1日1,000件でも
「全ユーザー合計で1日1,000冊の新規登録」まで耐えられる。初期段階では問題にならない。
