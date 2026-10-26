# Measure

Android app that measures objects from a top-down photo using a ruler or tape measure in the frame.

## Use

1. **Camera** or **Gallery**: load a straight-down photo with a ruler next to the object.
2. **Scale**: tap two ruler marks (far apart), enter the real distance.
3. **Outline**: tap the object. It is outlined automatically; drag points to fix it.
   Labels: length and width (smallest rotated bounding rectangle), area, perimeter, and diameter for round objects.
4. **Measure**: tap any two points for a point-to-point distance.

Pinch to zoom. A magnifier appears while dragging a point. Units toggle between mm, cm and in.

## Accuracy limits

- One top-down photo gives the 2D footprint only. Height can't be measured.
- The ruler must be at the same height as the surface being measured. Scale error is roughly
  (height difference) / (camera height); e.g. 5 cm difference with the camera 30 cm up is about 17%.
- Tilt of the phone causes perspective error; shoot as close to straight down as possible.
- Tap placement limits resolution; the Scale hint shows mm per pixel. Use the longest ruler span you can.

## Install

Every push to `main` builds the APK with GitHub Actions and publishes it to the
[Releases](../../releases) page (newest is marked Latest). Download `Measure.apk` on the phone and install it.

## Build

Kotlin, Jetpack Compose, OpenCV (Maven Central `org.opencv:opencv`). `./gradlew testDebugUnitTest assembleDebug`.
