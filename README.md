 2K Face Maker — Android source project

Offline-first Android app prototype for creating/editing NBA 2K20 cyberface texture-map starting points. It imports images through Android's file picker, generates five 2048×2048 PNG maps, provides simple brightness/contrast editing, and exports to both app-private internal storage and `Pictures/2KFaceMaker` in shared storage.

## Maps
- `diffuse_2048.png`: resized input photo/texture
- `normal_2048.png`: approximate tangent-space normal derived from image luminance gradients
- `specular_2048.png`: luminance-based starting specular map
- `face_detail_2048.png`: edge/detail map
- `hair_2048.png`: thresholded dark-pixel mask

## Build using only an Android phone
See [`PHONE_BUILD_GUIDE.md`](PHONE_BUILD_GUIDE.md). A GitHub Actions workflow in `.github/workflows/build-apk.yml` attempts to compile the debug APK on a hosted runner. You need a GitHub account and internet for the build. The APK has not been compiled or tested in this environment; the workflow does not guarantee the first build succeeds.

For a computer build, open this folder in Android Studio and build the debug APK normally.

## Important limitations
- This is an image-processing starter app, not a complete 3D UV unwrapping or Blender replacement.
- Generated maps are heuristics. For game-ready results, the source must be aligned to the exact NBA 2K20 player-head UV atlas and any required channel/format conventions.
- Hair mask generation is simple thresholding and needs manual cleanup for useful hair cutouts.
- The app works offline after installation; first Gradle build needs internet to fetch dependencies.
