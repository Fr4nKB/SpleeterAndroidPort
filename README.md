# SpleeterAndroidPort
I spent multiple days to find a way to have an audio stem separation tool on Android, this repo contains a `AudioStemSeparation` class to use the converted tflite model of the 4stems spleeter model available in the [jinay1991's repo](https://github.com/jinay1991/spleeter) from which this code was inspired (the tflite model can be downloaded [here](https://github.com/jinay1991/spleeter/releases/)).

## Configuration
-  Place the `AudioStemSeparation` class where you need
-  Place the tflite model in the `assets` folder of the app

## Usage
```
val separator = AudioStemSeparation(context, "nameModelWithoutExt", "path/to/file/file.wav")
separator.init()
separator.separate()
separator.unInit()
```
If the file you are using is large and you get errors with memory consumption try adding `android:largeHeap="true"` to AndroidManifest under `application` tag.

The `AudioStemSeparation` class automatically saves the stems as `0.wav`, `1.wav` and so on.. representing `bass`, `drums`, `accompaniment` and `vocals`.
