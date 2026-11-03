module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    // A warning, not an error: a long body line (a URL, a pasted log)
    // costs nothing, and once such a commit is on a branch the only fix
    // is rewriting history that others have already built on.
    "body-max-line-length": [1, "always", 100],
  },
};
