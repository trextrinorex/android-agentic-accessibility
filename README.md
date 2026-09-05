# Android Agentic Accessibility

A voice-first Android AI agent that observes Android accessibility UI state, reasons with a configurable LLM, and acts through Android AccessibilityService.

## Status

Version 1 MVP scaffold: accessibility service, semantic UI observation, action execution, LLM planning, safety confirmation, task memory, voice input, and text-to-speech are included as modular components.

## Safety

This app is designed to keep the user in control. High-impact actions require confirmation. It does not bypass authentication, CAPTCHA, biometrics, payment confirmation, or Android security controls. API keys and screen content must be treated as sensitive.

## Build

1. Open the repository in Android Studio.
2. Allow Gradle to sync.
3. Build the `app` debug variant.
4. Install it on an Android device.
5. Open the app and enable its Accessibility Service in Android Settings.
6. Configure the LLM endpoint/model and API key.
7. Use push-to-talk or type a task, then start the agent.

## MVP test sequence

- Open Settings
- Open YouTube
- Open YouTube and search for cats
- Open a specified app
- Type text into a visible field
- Stop/cancel a running task

## Architecture

`UI -> AgentController -> TaskManager -> LLMClient / AgentPlanner -> UIObserver -> SafetyManager -> ActionExecutor -> AccessibilityService -> Android Apps`

Voice flows through `SpeechToText` into the same agent controller and responses can be spoken through `TextToSpeech`.
