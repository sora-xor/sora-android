export const TAIRA_DEPLOYMENT_CANDIDATE_MAXIMUM_AGE_SECONDS_V1 = 6 * 60 * 60;
export const TAIRA_DEPLOYMENT_ROLLOUT_MAXIMUM_AGE_SECONDS_V1 =
  7 * 24 * 60 * 60;

const ROLLOUT_TARGETS = new Set(["1", "5", "25", "100"]);

/**
 * Candidate qualification requires a fresh admission. Staged rollout consumes
 * that immutable packaged admission across three mandatory 48-hour dwells, so
 * it uses the shared seven-day rollout window. Unknown stages fail closed.
 */
export const maximumTairaDeploymentAdmissionAgeSecondsV1 = (targetRaw) => {
  if (targetRaw === "") {
    return TAIRA_DEPLOYMENT_CANDIDATE_MAXIMUM_AGE_SECONDS_V1;
  }
  return ROLLOUT_TARGETS.has(targetRaw)
    ? TAIRA_DEPLOYMENT_ROLLOUT_MAXIMUM_AGE_SECONDS_V1
    : null;
};
