# Jenkins build scripts

## lineage-targets.json

Seeds the job for lineage-build-kicker, which loops over the device entries and spawns lineage-14.1 jobs for each. The parameters here line up with the variables passed to the bash stages in lineage-14.1

Parameters:

| Parameter | Values/Examples | Description | Default |
| --------- | --------------- | ----------- | ------- |
| `device` | `angler`, `bullhead`, `marlin` | device codename for the device to build. These are typically all lowercase and usually bear no relation to the consumer name for the device | `HELP-omgwtfbbq` (will fail the build) |
| `build_type` | `user`, `userdebug`, `eng` | the type of build to make. See the differences in the [Android docs](https://source.android.com/source/add-device#build-variants) | `userdebug` |
| `ota` | `true`, `false` | If true, the build is uploaded to [OTA server](https://lineage.harryyoud.co.uk), where it can be distributed over the air to devices | `true` |
| `repopick_nums` | `157953,172537` | Comma separated list of changes to download from the [LineageOS Gerrit](https://review.lineageos.org) | (blank) |
| `repopick_tops` | `network-traffic,long-press-power-torch-timeout` | Comma separated list of topics to download from the [LineageOS Gerrit](https://review.lineageos.org) | (blank) |
| `with_su` | `true`, `false` | If true, the build will be pre-rooted with the LineageOS root solution. If not, a root package can be downloaded from [LineageOS](https://download.lineageos.org/extras) | `false` |
| `with_gapps` | `true`, `false` | If true, the build will be built with `$WITH_GAPPS` set to `true`. Requires `vendor/gapps/arm64/arm64-vendor.mk` to be inherited by `device.mk` with a `WITH_GAPPS` conditional to do anything meaningful | `false` |
| `with_dexpreopt` | `true`, `false` | If true, some apps are pre-optimised during the build. This dramatically increases build time and RAM usage during the build, but decreases the time spent on the first boot after an OTA update | `false` |
| `signed` | `true`, `false` | If true, builds will be signed with the keys in the directory specified in `lineage-14.1`. Beware, moving between non-signed and signed builds requires a factory reset or [migration build](https://wiki.lineageos.org/signing_builds.html#changing-keys) | `false` |
| `signed_backuptool` | `true`, `false` | If `signed` is true, and this is false, backuptool will not be included in the build, allowing `/system` to be left untouched to preserve verity | `true` |

## lineage-build-kicker

This downloads and interprets the json targets file, syncs the mirror (and waits for this to complete), and spawns the `lineage-14.1` jobs with the parameters above. If they aren't specified, the defaults are assumed.

If it is given a `device` parameter (eg, when launched manually), it loops over the JSON until reaching the device wanted, and spawns `lineage-14.1` for it.

## lineage-mirror-sync

Very simply just runs `repo sync` in the mirror directory

## lineage-14.1

Builds lineage-14.1 for a number of devices with specified parameters. The parameters have almost identical names to those used in `lineage-targets.json`

Stages:
1. Sync mirror
  - Sync local mirror of all LineageOS repos
  - If this is an automatic (timed) job (eg. triggered by `lineage-build-kicker`), this stage is skipped, as it was already done in the `lineage-build-kicker` job
2. Input manifest
  - Delete files in `.repo/local_manifests/`
  - Download `./manifest.xml` to .repo/local_manifests
3. Sync
  - Reset all repos to `HEAD`
  - Sync from mirror
  - Reset all repos to `HEAD`
  - Run breakfast against device name
4. Output manifest
  - Export an xml with all the repos and their current HEAD
  - This is later added as an artifact, so builds can be reproduced if needed
5. Repopicks
  - Loop over `repopick_nums` and `repopick_tops` and force pick, regardless of status (abandoned or draft etc.)
6. Clean
  - Clean out/ with `make clean`
7. Build
  - Export `ccache` variables
  - Run lunch for device
  - Kill `jack-server` and relaunches with a greater heap size
  - Build required target (`bootimage` or `bacon`)
  - Kill `jack-server`
8. Sign build
  - If `signed` is true, remove produced zip, and sign `target_files.zip`
9. Upload to jenkins
  - Upload manifest produced in (4), and `installed-files.txt` to Jenkins
  - If OTA is set to false, upload the build alongside the md5sum (or just bootimage)
10. Upload to www
  - Upload build and md5sum to www server
11. Add to updater
  - Use API key to add build to the `lineageos_updater`, linked to www upload

## lineage-contributor-cloud

The contributor cloud found in `Settings > About phone > Contributors` is found in android_packages_apps_CMParts in cm-14.1, and android_packages_apps_Settings in cm-13.0

The script (found in LineageOS/contributors-cloud-generator) downloads all the repos from the LineageOS GitHub and creates an index from all of the authors of commits and builds a simple wordcloud of these

Be aware, the typical run time, providing you have the repos already synced, is approximately 4 hours.

Stages:
1. Pull
  - Sync CMParts (for cm-14.1), Settings (for cm-13.0) and contributors-cloud-generator
2. Build prerequistes
  - Build jar for generating cloud
3. Cleanup
  - Remove artificats from last build
4. Build cloud
  - Runs the generator generating a database, a PNG and an SVG
5. Create draft changes
  - For cm-14.1:
    - Copy database to CMParts and add to git index
    - Download Gerrit commit-msg hook (for ChangeID)
    - Commit and adjust author, then upload to Gerrit
  - For cm-13.0:
    - Copy database to Settings and add to git index
    - Download Gerrit commit-msg hook (for ChangeID)
    - Commit and adjust author, then upload to Gerrit
6. Archive output
  - Uploads the built files to Jenkins

## lineage-cve-json

The LineageOS cve_tracker uses a JSON file to determine what kernel particular devices depend upon. This can be generated using the scripts [here](https://github.com/LineageOS/scripts/tree/master/device-deps-regenerator)

**Warning**: I recommend running this infrequently (once a week or so) to avoid triggering GitHub's API abuse detection mechanisms

Stages:
1. Clone
  - Sync cve_tracker and scripts repo
  - Set GitHub API key
  - Download Gerrit `commit-msg` hook
2. Generate JSON
  - Run script to scrape all the JSON
  - Run another script to generate a kernel=>device mappings JSON
3. Compare
  - If the resulting JSON is different to the one already present, continue, otherwise bail out
4. Create draft change
  - Add new json to git index, commit and push to Gerrit as a draft

## lineage-mirror-manifest

LineageOS maintains a mirror manifest; a mechanism of using repo to store a complete mirror of the LineageOS GitHub for faster checkout of different branches. This needs regenerating occasionally to include newly forked/created repos

**Warning**: I recommend running this infrequently (once a week or so) to avoid triggering GitHub's API abuse detection mechanisms

Stages:
1. Clone
  - Clone LineageOS/mirror
2. Generate mirror
  - Move `default.xml` out of the way
  - Run script to generate mirror
3. Compare
  - If new file differs from old, continue, otherwise, bail out
4. Create draft change
  - Add new xml to git index, commit and push to Gerrit as a draft

## lineage-updater-json

The LineageOS updater web app uses a JSON file to determine what repos particular devices depend upon to determine relevant changelogs for each device. This can be generated using the scripts [here](https://github.com/LineageOS/scripts/tree/master/device-deps-regenerator)

**Warning**: I recommend running this infrequently (once a week or so) to avoid triggering GitHub's API abuse detection mechanisms

Stages:
1. Clone
  - Sync lineageos_updater and scripts repo
  - Set GitHub API key
  - Download Gerrit `commit-msg` hook
2. Generate JSON
  - Run script to scrape all the JSON
  - Run another script to generate a device mappings JSON
3. Compare
  - If the resulting JSON is different to the one already present, continue, otherwise bail out
4. Create draft change
  - Add new json to git index, commit and push to Gerrit as a draft

## themuppets-mirror-manifest

TheMuppets maintains a mirror manifest; a mechanism of using repo to store a complete mirror of the TheMuppets GitHub for faster checkout of different branches. This needs regenerating occasionally to include newly forked/created repos

**Warning**: I recommend running this infrequently (once a week or so) to avoid triggering GitHub's API abuse detection mechanisms

Stages:
1. Clone
  - Clone TheMuppets/manifests. and checkout the `mirror` branch
2. Generate mirror
  - Move `default.xml` out of the way
  - Run script to generate mirror
3. Compare
  - If new file differs from old, continue, otherwise, bail out
4. Create draft change
  - Add new xml to git index, commit and push to GitHub

## mindthegapps

MindTheGapps is intended to be a minimal and simple as possible addon package that can be flashed in a custom recovery to obtain Google Apps on a custom ROM. These are often rebuilt using monthly releases from Google intended for Nexus devices. When a change on MindTheGapps/vendor_gapps is pushed, this job is triggered

Stages:
1. Clone
  - Clone MindTheGapps/vendor_gapps
2. Loop
  - Loop over the targets from `./mindthegapps-targets` and define a stage for each in which `make $arch` is done
3. Upload artifacts
  - Upload produced MindTheGapps zips to an AndroidFileHost folder
