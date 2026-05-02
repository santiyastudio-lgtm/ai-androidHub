# Intent API Integration

`SantiyaLocalAiHub` reserves public Android actions for simple delegated flows.

Actions:

```kotlin
com.santiya.localaihub.action.PICK_MODEL
com.santiya.localaihub.action.RUN_TEXT
com.santiya.localaihub.action.RUN_IMAGE
com.santiya.localaihub.action.RUN_VISION
```

Extras:

```kotlin
com.santiya.localaihub.extra.REQUEST_JSON
com.santiya.localaihub.extra.RESULT_JSON
com.santiya.localaihub.extra.ERROR
```

Model picker example:

```kotlin
val intent = Intent("com.santiya.localaihub.action.PICK_MODEL").apply {
    setPackage("com.santiya.localaihub")
}
launcher.launch(intent)
```

The current build returns the catalog JSON for `PICK_MODEL`. Execution actions return a compatibility guard and direct apps to the AIDL SDK, because long-running AI jobs need streaming callbacks, cancellation, foreground service visibility, and user-approved model selection.
