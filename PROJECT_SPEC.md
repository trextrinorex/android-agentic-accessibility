# Project layout

```text
app/src/main/java/com/trex/agenticaccessibility/
  MainActivity.kt
  SettingsActivity.kt
  agent/AgentController.kt
  agent/TaskMemory.kt
  accessibility/AgentAccessibilityService.kt
  accessibility/AccessibilityBridge.kt
  accessibility/UiObserver.kt
  ai/AgentModels.kt
  ai/LlmClient.kt
  safety/SafetyManager.kt
  security/SecureStore.kt
  voice/Voice.kt
```

## Configuration

The MVP accepts any OpenAI-compatible chat-completions endpoint. The endpoint, model, and API key are stored locally; the key is encrypted using Android Keystore-backed AES-GCM.

## Important Android limitations

Accessibility capabilities vary by app and Android version. Some apps expose incomplete or intentionally protected UI trees. Screenshots, authentication, CAPTCHAs, biometrics, payment flows, and protected content are not bypassed.

## Next milestones

- Add a dedicated Compose/Material 3 settings UI.
- Add screenshot/vision observations behind explicit privacy controls.
- Add persistent conversation context with redaction.
- Add app-label/package registry.
- Add voice confirmation for sensitive actions.
- Add a trusted-rule UI with explicit scope and revocation.
- Add instrumented tests on physical Android devices.
