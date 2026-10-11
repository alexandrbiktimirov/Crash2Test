export default {
  branches: ["master"],
  repositoryUrl: "https://github.com/alexandrbiktimirov/Crash2Test.git",
  tagFormat: "v${version}",
  plugins: [
    ["@semantic-release/commit-analyzer", { preset: "conventionalcommits" }],
    ["@semantic-release/release-notes-generator", { preset: "conventionalcommits" }],
    "./scripts/marketplace-release.mjs",
    [
      "@semantic-release/github",
      {
        assets: [{ path: "build/distributions/*-signed.zip", label: "Signed IntelliJ plugin" }],
        successCommentCondition: false,
        failCommentCondition: false,
        failTitle: false,
        releasedLabels: false,
      },
    ],
  ],
};
