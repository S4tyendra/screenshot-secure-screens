# GetUI

GetUI is an Android application designed to dump the UI hierarchy of any app into a static, pixel-perfect HTML/Tailwind CSS representation. It leverages Gemini AI to reconstruct the layout and Lucide icons for a clean, shareable UI overview.

## Features

- **Quick UI Dumping**: Capture the active window's XML hierarchy using a Quick Settings tile.
- **AI-Powered Reconstruction**: Uses Gemini 3.1 Flash Lite to convert accessibility XML into structured HTML with Tailwind CSS.
- **Offline Assets**: Bundles Tailwind CSS and Lucide Icons locally in the app assets.
- **Image Generation**: Automatically renders the generated HTML in a background WebView and saves it as a PNG.
- **Sharing**: Easily share the reconstructed UI image with other apps.
- **Customizable**: Configure your Gemini API key and fine-tune the system prompt in the app settings.

## Prerequisites

- Android 12 (API level 31) or higher.
- A Gemini API Key from [Google AI Studio](https://aistudio.google.com/).

## Getting Started

1. **Onboarding**: Enter your Gemini API key when prompted during the first launch.
2. **Permissions**: Enable the Accessibility Service to allow the app to read the UI hierarchy of other applications.
3. **Quick Tile**: Add the "Dump UI" tile to your Quick Settings panel for easy access.
4. **Capture**: Navigate to any app you want to capture and tap the "Dump UI" tile.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room (for storing dumps, HTML, and image paths)
- **AI**: Gemini Java SDK (`com.google.genai`)
- **Rendering**: Android WebView with local asset injection.

## License

MIT License
