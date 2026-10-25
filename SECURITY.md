# Security

Install this software only on devices you own or have permission to operate. Notification, location and camera access require explicit local setup and OS permissions. Do not paste pairing QR codes, device tokens, installation secrets, service-account keys, signing keys, real notifications, locations or camera images into public issues or build logs.

Report reproducible vulnerabilities using GitHub private vulnerability reporting when available. Otherwise contact the repository owner privately before sharing sensitive details. No independent security certification is claimed. The experimental camera iOS build and real-device behavior remain unverified.

Self-hosters must configure their own credentials, revoke lost devices and monitor API/KV usage. The code does not currently provide a separate pre-authentication IP limiter for every API; consider edge rate limits for exposed deployments. Hiding the server address is not authentication.
