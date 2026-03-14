# Activity calibration notes

This patch adds three beginner-friendly activities and updates baseline thresholds for existing drills:

- `STANDING_POSTURE_HOLD`
- `PUSH_UP`
- `SIT_UP`

Calibration choices were tuned conservatively for real-world phone-camera noise and beginner form variability:

- tighter stack and body-line thresholds for static standing posture
- lockout + depth + tempo emphasis for push-ups
- trunk range + controlled descent emphasis for sit-ups
- slower eccentric target for negative wall HSPU and sit-up descent control

## Internet research attempt

I attempted to fetch web references for exercise standards in this environment, but outbound HTTP(S) requests are blocked by the runtime proxy (tunnel 403). The values in this patch are therefore practical coaching defaults, meant for iterative field calibration with your recordings.
