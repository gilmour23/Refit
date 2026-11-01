# Offline demonstration entry — 2026-11-01

The demo-day app must open without a server URL, publishable key or account. When either backend setting is absent, launch the existing home and mock wrist exercise flow under the reserved local owner `__offline_demo_v1__`. Preserve mock-data labels and encrypted device storage. Account records must not appear in offline history, and local demo records must not be uploaded or presented as awaiting cloud synchronization. Hide account/logout controls in offline mode. A configured installation keeps its existing login and account ownership behavior. Sensor values remain simulated until the hardware protocol is supplied.

Existing sources are based on ab0039ce55e331144c3d74eac08d117a1bc196f3. This implements the user's approved server-free demonstration scope; it does not change the backend or signing identity.
