# F-Droid Inclusion & Publishing Guide: SMS & Message Forwarder

This document details the exact steps to submit **SMS & Message Forwarder** (`com.smsforwarder`) to the official [F-Droid main repository](https://gitlab.com/fdroid/fdroiddata).

---

## 1. Compliance Checklist (Completed)

- [x] **FOSS License**: GNU General Public License v3.0 (`LICENSE` file added).
- [x] **Zero Proprietary SDKs**: Zero Google Play Services, Firebase, tracking, or ad libraries. All dependencies are open-source (Apache 2.0).
- [x] **Builds 100% from Source**: Standard `./gradlew assembleRelease` compiles clean APK without proprietary dependencies.
- [x] **Fastlane Metadata**: Store metadata, descriptions, and high-res screenshots structured in `fastlane/metadata/android/en-US/`.
- [x] **Recipe Metadata**: Build recipe file ready at `metadata/com.smsforwarder.yml`.

---

## 2. Publishing Steps (Git & F-Droid Submission)

### Step 1: Push Project to a Public Git Remote (GitHub/GitLab/Codeberg)
```bash
git remote add origin https://github.com/your-username/sms-forwarder.git
git branch -M main
git push -u origin main
git push origin --tags
```

### Step 2: Fork F-Droid Data Repository
1. Log in to GitLab (or create an account at [gitlab.com](https://gitlab.com)).
2. Fork the official metadata repository: [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
3. Clone your fork locally or use the GitLab Web IDE.

### Step 3: Add the Build Recipe
Copy the recipe file `metadata/com.smsforwarder.yml` from this repository into the `metadata/` directory of your `fdroiddata` fork.
*Make sure `SourceCode` in the YAML points to your public repository URL.*

### Step 4: Open a Merge Request (MR)
1. Commit the new file in your branch: `Add com.smsforwarder`.
2. Open a Merge Request against `fdroid/fdroiddata:master`.
3. In the MR description, follow the F-Droid template:
   - State that the app is 100% open source under GPLv3.
   - Mention zero proprietary dependencies or analytics.
4. F-Droid CI will automatically execute a test build using `fdroid build`. Once verified and reviewed by maintainers, the MR is merged, and the app is indexed in the F-Droid client.
