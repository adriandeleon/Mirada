package com.mirada;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the release's trust boundaries where they are (Pauta QUAL-15, REL-15, REL-16, REL-18,
 * REL-19, REL-21, REL-28). The javafx-app-template's test, on Mirada's workflows and scripts.
 *
 * <p>None of this runs in a local build, and a regression is silent: a signing step moved after the
 * upload, an attestation that misses a download, a tag that publishes without its secrets, all run
 * exactly as well as the right ones until the day it matters. The checks are textual on purpose (the
 * workflows are YAML that nothing here parses), and each message says what the rule protects.
 */
class ReleaseSupplyChainTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));
    private static final Path WORKFLOWS = REPO.resolve(Path.of(".github", "workflows"));

    private static String read(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static String script(String name) throws IOException {
        return read(REPO.resolve(Path.of("scripts", name)));
    }

    private static List<Path> workflows() throws IOException {
        try (var files = Files.list(WORKFLOWS)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * {@code job id -> the text of that job}, split on the two-space-indented keys under {@code jobs:}.
     * Whole-line comments are dropped: the comment above a job would otherwise belong to the one before.
     */
    static Map<String, String> jobs(String workflow) {
        Map<String, String> jobs = new LinkedHashMap<>();
        int at = workflow.indexOf("\njobs:\n");
        assertTrue(at >= 0, "a workflow without `jobs:`");
        String body = workflow.substring(at + "\njobs:\n".length()).replaceAll("(?m)^\\s*#.*\\n", "");
        Matcher m = Pattern.compile("(?m)^  ([A-Za-z0-9_-]+):\\s*$").matcher(body);
        List<int[]> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            starts.add(new int[] {m.start(), m.end()});
            names.add(m.group(1));
        }
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < names.size() ? starts.get(i + 1)[0] : body.length();
            jobs.put(names.get(i), body.substring(starts.get(i)[1], end));
        }
        return jobs;
    }

    private static Map<String, String> releaseJobs() throws IOException {
        return jobs(read(WORKFLOWS.resolve("release.yml")));
    }

    private static String job(Map<String, String> jobs, String name) {
        String body = jobs.get(name);
        assertNotNull(body, "the workflow lost its `" + name + "` job");
        return body;
    }

    /** The text of the step with this name, up to the next step. */
    static String step(String job, String name) {
        int at = job.indexOf("- name: " + name + "\n");
        assertTrue(at >= 0, "the step `" + name + "` is gone");
        int next = job.indexOf("\n      - ", at + 1);
        return job.substring(at, next < 0 ? job.length() : next);
    }

    /** Asserts that each text appears in {@code in}, each after the one before. */
    private static void inOrder(String in, String why, String... texts) {
        int last = -1;
        for (String text : texts) {
            int at = in.indexOf(text, last + 1);
            assertTrue(at > last, why + " (`" + text + "` is missing or out of order)");
            last = at;
        }
    }

    // ---- every workflow ---------------------------------------------------------------------------------

    @Test
    void everyActionIsPinnedToAFullCommitShaWithItsVersionInAComment() throws IOException {
        Pattern uses = Pattern.compile("(?m)^\\s*(?:- )?uses:\\s*(.+)$");
        Pattern pinned = Pattern.compile("[\\w.-]+/[\\w./-]+@[0-9a-f]{40}\\s+#\\s*v?\\d\\S*");
        List<String> floating = new ArrayList<>();
        int seen = 0;
        for (Path workflow : workflows()) {
            Matcher m = uses.matcher(read(workflow));
            while (m.find()) {
                String action = m.group(1).trim();
                if (action.startsWith("./")) {
                    continue; // a workflow of this repository, at this commit
                }
                seen++;
                if (!pinned.matcher(action).matches()) {
                    floating.add(workflow.getFileName() + ": " + action);
                }
            }
        }
        assertTrue(seen > 10, "found only " + seen + " `uses:` lines; the scan is broken");
        assertTrue(
                floating.isEmpty(),
                "a tag or branch is mutable; pin each action as `owner/repo@<40-hex sha> # vX.Y.Z`: " + floating);
    }

    @Test
    void theWorkflowTokenIsReadOnlyUnlessAJobAsksForMore() throws IOException {
        for (Path workflow : workflows()) {
            assertTrue(
                    read(workflow).contains("\npermissions:\n  contents: read\n"),
                    workflow.getFileName() + " must default the token to `contents: read`");
        }
        for (var job : releaseJobs().entrySet()) {
            boolean writes = job.getValue().contains("contents: write");
            boolean mayWrite = job.getKey().equals("publish") || job.getKey().equals("bump");
            assertEquals(mayWrite, writes, "release.yml job `" + job.getKey() + "`: unexpected token scope");
        }
    }

    @Test
    void onlyTheJobThatPushesKeepsItsCheckoutCredentials() throws IOException {
        for (Path workflow : workflows()) {
            for (var job : jobs(read(workflow)).entrySet()) {
                String body = job.getValue();
                if (!body.contains("actions/checkout@")) {
                    continue;
                }
                assertEquals(
                        !body.contains("git push"),
                        body.contains("persist-credentials: false"),
                        workflow.getFileName() + " job `" + job.getKey()
                                + "`: a checkout keeps the token in .git/config unless persist-credentials is false,"
                                + " and only a job that pushes needs it");
            }
        }
    }

    @Test
    void everyJobHasATimeout() throws IOException {
        List<String> unbounded = new ArrayList<>();
        for (Path workflow : workflows()) {
            jobs(read(workflow)).forEach((name, body) -> {
                // A job that calls another workflow takes no timeout; that workflow's jobs carry theirs.
                boolean calls = body.contains("\n    uses: ./");
                if (!calls && !body.contains("\n    timeout-minutes:")) {
                    unbounded.add(workflow.getFileName() + ":" + name);
                }
            });
        }
        assertTrue(unbounded.isEmpty(), "a hung job otherwise holds a runner for six hours: " + unbounded);
    }

    // ---- ci.yml -----------------------------------------------------------------------------------------

    @Test
    void ciChecksFormatAndBuildAndTestOnEveryPullRequest() throws IOException {
        String ci = read(WORKFLOWS.resolve("ci.yml"));
        assertTrue(ci.contains("  push:\n    branches: [main]\n"), "ci.yml runs on every push to main");
        assertTrue(
                ci.contains("\n  pull_request:\n") && !ci.contains("  pull_request:\n    branches:"),
                "ci.yml runs on every pull request, into any branch");
        assertTrue(ci.contains("\n  workflow_dispatch:"), "the release's bump job dispatches CI on its branch");
        Map<String, String> jobs = jobs(ci);
        String format = job(jobs, "format");
        assertTrue(format.contains("name: Format\n"), "the required check is named Format");
        assertTrue(step(format, "Check formatting").contains("spotless:check"), "the Format job checks formatting");
        String test = job(jobs, "test");
        assertTrue(
                test.contains("name: Build & Test (${{ matrix.os }})\n"),
                "the required checks are named Build & Test (<os>); renaming them strands main's rule");
        assertTrue(step(test, "Verify").contains(" verify"), "Build & Test runs `./mvnw verify`");
        assertTrue(
                step(test, "Upload test reports").contains("if: always()"),
                "the test reports are uploaded when the tests fail: that is when they are needed");
    }

    // ---- release.yml ------------------------------------------------------------------------------------

    @Test
    void aTagPublishesAndADispatchIsADryRunThatPublishesNothing() throws IOException {
        String release = read(WORKFLOWS.resolve("release.yml"));
        assertTrue(release.contains("  push:\n    tags: ['v"), "a release starts from a version tag");
        assertTrue(release.contains("\n  workflow_dispatch:"), "a dry run starts from workflow_dispatch");
        Map<String, String> jobs = jobs(release);
        assertTrue(
                step(job(jobs, "preflight"), "Resolve and check the version")
                        .contains("does not match the pom version"),
                "a tag that disagrees with the pom must fail before anything builds");
        String publish = job(jobs, "publish");
        assertFalse(
                publish.contains("\n    if: "),
                "the publish job must run on a dry run too, so JReleaser is exercised before a tag");
        assertTrue(
                step(publish, "JReleaser")
                        .contains("arguments: ${{ github.event_name == 'workflow_dispatch' && 'full-release --dry-run'"
                                + " || 'full-release' }}"),
                "a workflow_dispatch run must run JReleaser with --dry-run");
        assertTrue(
                Pattern.compile("\\n\\s+version: \\d+\\.\\d+\\.\\d+\\n")
                        .matcher(step(publish, "JReleaser"))
                        .find(),
                "the JReleaser version the action downloads must be pinned (its default is `latest`)");
        for (String tagOnly : List.of("bump", "winget")) {
            assertTrue(
                    job(jobs, tagOnly).contains("if: github.event_name == 'push' && github.ref_type == 'tag'"),
                    "a dry run must not run `" + tagOnly + "`");
        }
        assertTrue(job(jobs, "bump").contains("needs: [preflight, publish]"), "main reopens after the release");
    }

    @Test
    void nothingIsPublishedBeforeEveryAssetIsChecked() throws IOException {
        Map<String, String> jobs = releaseJobs();
        assertTrue(job(jobs, "build").contains("needs: preflight"), "the legs wait for the version check");
        assertTrue(job(jobs, "sign-linux").contains("needs: build"), "sign-linux signs what the build uploaded");
        assertTrue(
                job(jobs, "check-assets").contains("needs: [preflight, sign-linux]"),
                "the asset check must see the signed Linux files");
        assertTrue(
                job(jobs, "publish").contains("needs: [preflight, check-assets]"),
                "JReleaser publishes an immutable release: only after the asset check passed");
        String check = step(job(jobs, "check-assets"), "Check the expected files");
        for (String expected : List.of(
                "mirada-signing-key.asc",
                "for ext in deb rpm tar.gz AppImage",
                "\"Mirada-$VERSION-$target.$ext.asc\"",
                "Mirada-$VERSION-macos-arm64.dmg",
                "Mirada-$VERSION-macos-x64.dmg",
                "Mirada-$VERSION-windows-x64.msi")) {
            assertTrue(check.contains(expected), "the release-asset check no longer expects " + expected);
        }
        assertTrue(check.contains("exit 1"), "a missing release asset must fail the run");
    }

    @Test
    void theMacOsDmgIsSignedBeforeTheBuildThenNotarizedStapledCheckedAndLaunched() throws IOException {
        String build = job(releaseJobs(), "build");
        inOrder(
                build,
                "the keychain must exist before -Pdist signs the app, and the dmg must be notarized before it"
                        + " is uploaded for the release",
                "- name: Import signing certificate (macOS)",
                "- name: Build, test and package",
                "- name: Notarize the dmg (macOS)",
                "- name: Name the installers",
                "- name: Upload\n");

        String importStep = step(build, "Import signing certificate (macOS)");
        for (String secret : List.of("MACOS_CERT_P12", "MACOS_CERT_PASSWORD", "APPLE_API_KEY_P8", "APPLE_API_KEY_ID")) {
            assertTrue(importStep.contains("secrets." + secret), "the import step no longer reads " + secret);
        }
        assertTrue(importStep.contains("id: macsign"), "the notarize step reads the import step's outputs");
        assertTrue(importStep.contains("scripts/ci-signing-credentials.sh"), "the import step runs the script");

        String credentials = script("ci-signing-credentials.sh");
        inOrder(
                credentials,
                "a tag without all four secrets must fail before anything is signed or built",
                "[ \"${GITHUB_EVENT_NAME:-}\" = push ] && [ \"${GITHUB_REF_TYPE:-}\" = tag ] && tag=true",
                "if $tag && [ \"${#missing[@]}\" -gt 0 ]; then",
                "exit 1",
                "security create-keychain");
        assertTrue(
                credentials.contains("Mirada Test Signing") && credentials.contains("notarize=$notarize"),
                "a dry run or a fork without the secrets signs with a throwaway identity, not notarized");
        assertTrue(
                credentials.contains("MIRADA_MAC_IDENTITY=")
                        && script("sign-mac.sh").contains("MIRADA_MAC_IDENTITY"),
                "-Pdist signs the bundle with the identity the import step hands it");

        String notarize = step(build, "Notarize the dmg (macOS)");
        assertTrue(
                notarize.contains("MIRADA_NOTARIZE: ${{ steps.macsign.outputs.notarize }}"),
                "notarization runs exactly when the import step had the Developer ID and the API key");
        for (String secret : List.of("APPLE_API_KEY_P8", "APPLE_API_KEY_ID", "APPLE_API_ISSUER")) {
            assertTrue(notarize.contains("secrets." + secret), "notarization no longer reads " + secret);
        }
        assertTrue(notarize.contains("scripts/notarize-mac.sh target/installers/*.dmg"), "the dmg is notarized");
        inOrder(
                script("notarize-mac.sh"),
                "sign the dmg, notarize, staple, check it the way Gatekeeper will, then launch the notarized app",
                "codesign --force --sign \"$identity\" --timestamp \"$dmg\"",
                "xcrun notarytool submit",
                "xcrun stapler staple",
                "spctl --assess --type open",
                "spctl --assess --type execute",
                "scripts/smoke-test.sh\" \"$app/Contents/MacOS/Mirada\"");

        String remove = step(build, "Remove the signing keychain (macOS)");
        assertTrue(
                remove.contains("if: always()") && remove.contains("security delete-keychain"),
                "the keychain holding the private key must be removed even when the leg fails");
    }

    @Test
    void theLinuxPackagesAreSignedOffTheBuildRunnersWithTheCommittedKey() throws IOException {
        Map<String, String> jobs = releaseJobs();
        assertFalse(
                job(jobs, "build").contains("LINUX_GPG_KEY"),
                "the key must never reach a build leg, which runs whatever Maven resolves");
        String sign = job(jobs, "sign-linux");
        String signStep = step(sign, "Sign");
        assertTrue(
                signStep.contains("secrets.LINUX_GPG_KEY") && signStep.contains("secrets.LINUX_GPG_PASSPHRASE"),
                "sign-linux reads the key and its passphrase from the secrets");
        inOrder(
                signStep,
                "a tag without the signing key must fail, not publish unsigned packages",
                "\"$GITHUB_REF_TYPE\" = tag ]; then",
                "exit 1");
        assertTrue(
                signStep.contains("MIRADA_GPG_EXPECTED_KEY=packaging/linux/signing-key.asc"),
                "a release must be signed by the committed key and no other");
        inOrder(
                signStep,
                "what was signed is checked against the public key alone before it is uploaded",
                "scripts/sign-linux-packages.sh",
                "scripts/check-linux-signatures.sh");
        assertTrue(
                script("sign-linux-packages.sh").contains("_gpgorigin"), "the deb carries a debsigs origin signature");
        for (String target : List.of("linux-x64", "linux-arm64")) {
            String upload = step(sign, "Upload signed (" + target + ")");
            assertTrue(
                    upload.contains("name: Mirada-" + target + "\n") && upload.contains("overwrite: true"),
                    "the signed " + target + " files must replace the build leg's unsigned ones");
        }

        String key = read(REPO.resolve(Path.of("packaging", "linux", "signing-key.asc")));
        assertTrue(
                key.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----")
                        && key.strip().endsWith("-----END PGP PUBLIC KEY BLOCK-----"),
                "packaging/linux/signing-key.asc must be the release key's armored public half");
        assertFalse(key.contains("PRIVATE"), "never commit the private key");
    }

    @Test
    void theChecksumsAreSignedByTheCommittedKeyAndATagFailsWithoutIt() throws IOException {
        String publish = job(releaseJobs(), "publish");
        String key = step(publish, "Release key for the checksums");
        assertTrue(key.contains("secrets.LINUX_GPG_KEY"), "the checksums are signed with the release key");
        assertTrue(key.contains("packaging/linux/signing-key.asc"), "and only with the committed key");
        inOrder(key, "a tag without the release key must fail", "\"$GITHUB_REF_TYPE\" = tag ]; then", "exit 1");
        inOrder(
                publish,
                "the key is in place before JReleaser signs with it",
                "- name: Release key for the checksums",
                "- name: JReleaser");
        String jreleaser = read(REPO.resolve("jreleaser.yml"));
        assertTrue(
                jreleaser.contains("signing:\n  pgp:\n    active: ALWAYS\n")
                        && jreleaser.contains("    mode: COMMAND\n")
                        && jreleaser.contains("    checksums: true\n"),
                "JReleaser signs checksums_sha256.txt through gpg (its in-memory signature does not verify)");
    }

    @Test
    void everyReleaseAssetGetsAProvenanceAttestationBeforeItIsPublished() throws IOException {
        Map<String, String> jobs = releaseJobs();
        for (var job : jobs.entrySet()) {
            assertEquals(
                    job.getKey().equals("publish"),
                    job.getValue().contains("id-token: write"),
                    "release.yml job `" + job.getKey() + "`: only the publish job may mint an OIDC token");
        }
        String publish = job(jobs, "publish");
        assertTrue(publish.contains("attestations: write"), "the publish job cannot store its attestations");
        inOrder(
                publish,
                "attest before JReleaser publishes the immutable release",
                "actions/attest-build-provenance@",
                "jreleaser/release-action@");

        assertEquals(
                attachedByJReleaser(read(REPO.resolve("jreleaser.yml"))),
                attested(publish),
                "every file JReleaser attaches must be attested, and nothing else: keep the attestation's"
                        + " subject-path and jreleaser.yml's files.globs the same list");
    }

    /** The attestation step's {@code subject-path} patterns, sorted. */
    static List<String> attested(String publishJob) {
        Matcher subjects = Pattern.compile("subject-path: \\|\\n((?: {12}\\S.*\\n)+)")
                .matcher(step(publishJob, "Attest build provenance") + "\n");
        assertTrue(subjects.find(), "the attestation step lost its `subject-path: |` list");
        return subjects.group(1).lines().map(String::trim).sorted().toList();
    }

    /**
     * What JReleaser attaches, as globs, sorted: each distribution's artifact {@code path} (its
     * {@code {{projectVersion}}} read as {@code *}), if it has distributions, and each
     * {@code files.globs} pattern.
     */
    static List<String> attachedByJReleaser(String jreleaser) {
        List<String> out = new ArrayList<>();
        int files = jreleaser.indexOf("\nfiles:\n");
        assertTrue(files >= 0, "jreleaser.yml lost `files:`");
        int distributions = jreleaser.indexOf("\ndistributions:\n");
        if (distributions >= 0) {
            Matcher paths = Pattern.compile("(?m)^\\s+- path: '(.+)'$")
                    .matcher(jreleaser.substring(distributions, files > distributions ? files : jreleaser.length()));
            while (paths.find()) {
                out.add(paths.group(1).replace("{{projectVersion}}", "*"));
            }
        }
        Matcher globs = Pattern.compile("(?m)^    - pattern: '(.+)'$").matcher(jreleaser.substring(files));
        int patterns = 0;
        while (globs.find()) {
            out.add(globs.group(1));
            patterns++;
        }
        assertTrue(patterns > 0, "found no `files.globs` in jreleaser.yml; the scan is broken");
        return out.stream().sorted().toList();
    }

    @Test
    void theAttestedListMatchesWhatJReleaserAttachesAndNothingElse() {
        String jreleaser = """
                distributions:
                  app:
                    artifacts:
                      - path: 'artifacts/Mirada-{{projectVersion}}-macos-arm64.dmg'
                files:
                  globs:
                    - pattern: 'artifacts/Mirada-*.deb'
                """;
        String publish = """
                      - name: Attest build provenance
                        uses: actions/attest-build-provenance@0000000000000000000000000000000000000000 # v4.2.2
                        with:
                          subject-path: |
                            artifacts/Mirada-*.deb
                            artifacts/Mirada-*-macos-arm64.dmg

                      - name: JReleaser
                """;
        assertEquals(attachedByJReleaser("\n" + jreleaser), attested(publish));
        String missing = publish.replace("            artifacts/Mirada-*.deb\n", "");
        assertNotEquals(
                attachedByJReleaser("\n" + jreleaser),
                attested(missing),
                "a download JReleaser attaches but nothing attests must fail the check");
    }
}
