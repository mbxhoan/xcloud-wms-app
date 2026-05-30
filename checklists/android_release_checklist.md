# Android Release Checklist

## Before build

- [ ] Correct environment: dev/staging/prod.
- [ ] Correct API base URL.
- [ ] App version bumped.
- [ ] Version code bumped.
- [ ] Debug logs reduced for release.
- [ ] No hard-coded credentials.
- [ ] No token/password in logs.
- [ ] Release notes prepared.

## Signing

- [ ] Keystore exists.
- [ ] Keystore stored outside git.
- [ ] Password stored in company password manager.
- [ ] Signed APK/AAB generated.
- [ ] Signature verified by installing on test PDA.

## Smoke test

- [ ] Install fresh.
- [ ] Upgrade from previous version.
- [ ] Login/logout.
- [ ] Warehouse switch.
- [ ] Hardware scan on PDA.
- [ ] Stock lookup.
- [ ] PA draft + submit on staging.
- [ ] GI pick on staging.
- [ ] GR receive on staging.
- [ ] IC count on staging.

## Rollout

- [ ] Backup old APK.
- [ ] New APK uploaded to internal distribution/MDM.
- [ ] Install guide sent to tester.
- [ ] Rollback path documented.
- [ ] Support contact ready.
