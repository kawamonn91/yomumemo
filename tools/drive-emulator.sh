#!/usr/bin/env bash
# エミュレータ上のヨムメモを操作するための補助関数。
#
# 注意点が2つある。
#
# 1. エミュレータは描画が遅く、再インストール直後の初回起動は7秒以上かかることがある。
#    固定秒数の sleep で操作すると空振りし、しかも「タップは成功した」ように見えるため、
#    不具合の切り分けを誤らせる。目的の状態になるまで待つ形に統一する。
#
# 2. Compose のボタンは uiautomator のツリー上で text も content-desc も空になる。
#    文字列でボタンを探すことはできないので、クリック可能なノードの bounds で指す。
#    文字列の部分一致でタップ位置を決めると、説明文の中の同じ語に一致して
#    画面中央を叩いてしまう(例: 空の本棚にある「『本を追加』から…」)。
set -u

PKG="jp.yomumemo.app.debug"
ACTIVITY="$PKG/jp.yomumemo.app.MainActivity"

ui_dump() {
    adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n'
}

top_activity() {
    adb shell dumpsys activity activities 2>/dev/null |
        grep -m1 "topResumedActivity" | sed 's/.*ActivityRecord{[^ ]* [^ ]* //; s/ .*//'
}

wait_for_activity() {
    local needle="$1" timeout="${2:-60}" waited=0
    while [ "$waited" -lt "$timeout" ]; do
        case "$(top_activity)" in
            *"$needle"*) return 0 ;;
        esac
        adb shell sleep 1 >/dev/null 2>&1
        waited=$((waited + 1))
    done
    echo "TIMEOUT: '$needle' が前面に出ませんでした (現在: $(top_activity))" >&2
    return 1
}

# 画面に文字列が現れるまで待つ
wait_for_text() {
    local needle="$1" timeout="${2:-45}" waited=0
    while [ "$waited" -lt "$timeout" ]; do
        if ui_dump | grep -q "$needle"; then return 0; fi
        adb shell sleep 1 >/dev/null 2>&1
        waited=$((waited + 1))
    done
    echo "TIMEOUT: 画面に '$needle' が現れませんでした" >&2
    return 1
}

screen_texts() {
    ui_dump | grep -o 'text="[^"]\+"' | sed 's/^text="//; s/"$//' | sort -u
}

tap_xy() {
    adb shell input tap "$1" "$2"
}

# クリック可能なノードの bounds を列挙する (x1 y1 x2 y2 の4値を1行ずつ)
clickable_bounds() {
    ui_dump | grep 'clickable="true"' |
        grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' |
        sed 's/[^0-9]\+/ /g; s/^ //; s/ $//'
}

# N番目(1始まり)のクリック可能ノードの中心をタップする
tap_clickable() {
    local n="$1" line
    line=$(clickable_bounds | sed -n "${n}p")
    if [ -z "$line" ]; then echo "クリック可能ノード #$n が見つかりません" >&2; return 1; fi
    set -- $line
    tap_xy $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# 最も下にあるクリック可能ノード = だいたい FAB や主ボタン
tap_lowest_clickable() {
    local line
    line=$(clickable_bounds | sort -k2 -n | tail -1)
    if [ -z "$line" ]; then echo "クリック可能ノードがありません" >&2; return 1; fi
    set -- $line
    tap_xy $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

restart_app() {
    adb shell am force-stop "$PKG"
    adb shell am start -n "$ACTIVITY" >/dev/null 2>&1
    wait_for_activity "MainActivity" 90 || return 1
    adb shell sleep 2 >/dev/null 2>&1
}

shot() {
    adb exec-out screencap -p > "$1" 2>/dev/null
    echo "  撮影: $1 ($(wc -c < "$1") bytes)"
}

# text 属性が完全一致するノードの中心をタップする。
# Compose のボタンでも、中の Text ノードは text を持つことが多く、
# その中心はボタンの内側に入るので実用上これで押せる。
# 部分一致にすると説明文の中の同じ語を叩いてしまうので完全一致にする。
tap_exact_text() {
    local needle="$1" line
    line=$(ui_dump | grep "text=\"$needle\"" |
        grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' |
        sed 's/[^0-9]\+/ /g; s/^ //; s/ $//' | head -1)
    if [ -z "$line" ]; then echo "NOT FOUND: text=\"$needle\"" >&2; return 1; fi
    set -- $line
    tap_xy $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
