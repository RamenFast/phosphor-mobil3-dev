# R14 default-source startup: source checkpoint

2026-09-10. Grok OAuth implementation and independent Grok OAuth code review.

## Outcome

Configured default is not last-used. Default none. Process-death is a fresh launch. Capture never auto-starts MediaProjection unless automatic permission popup is on. Mic auto-starts only with RECORD_AUDIO or popup. Import does not confirm a default.

- Attempt 1 **7/10 FAIL**. Attempt 2 **8/10 PASS**.
- 954 tests + lint at review time.
- Duplicate built-in mic labels disambiguated by id.

## Not done

ASUS fresh-launch/process-death matrix. Root auto-start stays deferred.
