# Jamon Play release identity

This project must continue the existing Google Play application. A release is valid only when all of the following match.

- Application ID: `com.easternwood.sleeproutine`
- Next version code: `12` or greater (`11` was already uploaded)
- Upload key alias: `Jamon Upload`
- Upload certificate SHA-1: `12:90:5E:46:9C:D1:3F:66:40:42:8B:11:77:F9:C1:97:67:7B:46:76`
- Upload certificate SHA-256: `33:5A:8C:D6:A3:DA:FB:A4:6A:48:7B:20:13:F1:B2:17:A0:4B:EA:E3:FD:97:B3:52:F8:D7:3C:A1:E3:DB:9C:AF`

Release signing is intentionally read from environment variables so no private key is committed:

- `JAMON_KEYSTORE_PATH`
- `JAMON_KEYSTORE_PASSWORD`
- `JAMON_KEY_ALIAS`
- `JAMON_KEY_PASSWORD`

Run `scripts/verify_upload_keystore.sh` before producing the Play AAB. It refuses a keystore whose certificate does not match the existing Play upload key.

## AdMob release identity

Jamon uses no Google Play Billing. Free users can explicitly watch a rewarded ad to open Pro for 24 hours. Sleep playback and background playback never trigger an ad.

- AdMob app ID: `ca-app-pub-9360550840761530~2835130981`
- Home banner ad unit: `ca-app-pub-9360550840761530/6726274534`
- 24-hour Pro rewarded ad unit: `ca-app-pub-9360550840761530/1522049310`

Debug builds must use Google sample ad IDs. The production IDs above are injected only into the release build type. Run `scripts/verify_admob_configuration.sh` before building either variant.
