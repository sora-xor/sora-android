import { isAbsolute, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./strict-evidence.mjs";

export const QUALIFICATION_WORKFLOW_RELATIVE_PATH =
  ".github/workflows/production_release_qualification.yml";
export const QUALIFIED_CANDIDATE_ARTIFACT_NAME =
  "sora-android-qualified-candidate";
export const PRODUCTION_ADMISSION_IDENTITY_KEYS = [
  "candidateAabSha256",
  "candidateAabBytes",
  "sourceRevision",
  "qualifiedAtEpochSeconds",
  "candidatePiProbeReceiptSha256",
  "qualificationRepository",
  "qualificationWorkflowPath",
  "qualificationWorkflowSha256",
  "qualificationRunId",
  "qualificationRunAttempt",
  "sora2NetworkRevision",
  "runtimeSpecVersion",
  "runtimeTransactionVersion",
  "runtimeMetadataSha256",
  "productionSigningIdentitySha256",
  "dependencySigningReviewManifestSha256",
  "irohaMobileSdkPinSha256",
  "migrationQualificationSha256",
  "tairaDeploymentManifestSha256",
  "tairaCanaryReceiptSha256",
  "minamotoCanaryReceiptSha256",
  "fundedCanaryTrustSha256",
  "productionRolloutTrustSha256",
];

const MAXIMUM_API_RECEIPT_BYTES = 256 * 1024;
const MAXIMUM_WORKFLOW_BYTES = 256 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;
const REPOSITORY = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;
const positiveSafeInteger = (value) =>
  Number.isSafeInteger(value) && value > 0;

export const validateQualifiedCandidateProvenance = ({
  root,
  admissionIdentity,
  expectedRepository,
  expectedSourceRevision,
  candidateRunIdRaw,
  candidateRunReceiptPath,
  candidateArtifactReceiptPath,
}) => {
  if (
    typeof root !== "string" ||
    !isAbsolute(root) ||
    resolve(root) !== root ||
    admissionIdentity === null ||
    typeof admissionIdentity !== "object" ||
    Array.isArray(admissionIdentity) ||
    !REPOSITORY.test(expectedRepository ?? "") ||
    !/^[0-9a-f]{40}$/.test(expectedSourceRevision ?? "") ||
    !/^[1-9][0-9]{0,14}$/.test(candidateRunIdRaw ?? "") ||
    !canonicalAbsolutePath(candidateRunReceiptPath) ||
    !canonicalAbsolutePath(candidateArtifactReceiptPath)
  ) {
    return null;
  }

  const candidateRunId = Number(candidateRunIdRaw);
  if (!positiveSafeInteger(candidateRunId)) return null;

  const workflow = hashStableRegularFile(
    resolve(root, QUALIFICATION_WORKFLOW_RELATIVE_PATH),
    MAXIMUM_WORKFLOW_BYTES,
  );
  const runRecord = readStrictJsonFile(
    candidateRunReceiptPath,
    MAXIMUM_API_RECEIPT_BYTES,
  );
  const artifactRecord = readStrictJsonFile(
    candidateArtifactReceiptPath,
    MAXIMUM_API_RECEIPT_BYTES,
  );
  if (workflow === null || runRecord === null || artifactRecord === null) {
    return null;
  }

  const run = runRecord.value;
  const expectedRunApiUrl =
    `https://api.github.com/repos/${expectedRepository}/actions/runs/${candidateRunId}`;
  if (
    run === null ||
    typeof run !== "object" ||
    Array.isArray(run) ||
    run.id !== candidateRunId ||
    run.run_attempt !== 1 ||
    run.event !== "workflow_dispatch" ||
    run.status !== "completed" ||
    run.conclusion !== "success" ||
    run.head_sha !== expectedSourceRevision ||
    run.path !== QUALIFICATION_WORKFLOW_RELATIVE_PATH ||
    run.repository?.full_name !== expectedRepository ||
    run.head_repository?.full_name !== expectedRepository ||
    run.url !== expectedRunApiUrl ||
    run.artifacts_url !== `${expectedRunApiUrl}/artifacts` ||
    !positiveSafeInteger(run.workflow_id)
  ) {
    return null;
  }

  const artifacts = artifactRecord.value;
  if (
    artifacts === null ||
    typeof artifacts !== "object" ||
    Array.isArray(artifacts) ||
    artifacts.total_count !== 1 ||
    !Array.isArray(artifacts.artifacts) ||
    artifacts.artifacts.length !== 1
  ) {
    return null;
  }
  const artifact = artifacts.artifacts[0];
  if (
    artifact === null ||
    typeof artifact !== "object" ||
    Array.isArray(artifact) ||
    !positiveSafeInteger(artifact.id) ||
    artifact.name !== QUALIFIED_CANDIDATE_ARTIFACT_NAME ||
    artifact.expired !== false ||
    !positiveSafeInteger(artifact.size_in_bytes) ||
    typeof artifact.digest !== "string" ||
    !/^sha256:[0-9a-f]{64}$/.test(artifact.digest) ||
    artifact.workflow_run?.id !== candidateRunId ||
    artifact.workflow_run?.head_sha !== expectedSourceRevision ||
    artifact.archive_download_url !==
      `https://api.github.com/repos/${expectedRepository}/actions/artifacts/${artifact.id}/zip`
  ) {
    return null;
  }

  if (
    admissionIdentity.qualificationRepository !== expectedRepository ||
    admissionIdentity.qualificationWorkflowPath !==
      QUALIFICATION_WORKFLOW_RELATIVE_PATH ||
    admissionIdentity.qualificationWorkflowSha256 !== workflow.sha256 ||
    admissionIdentity.qualificationRunId !== candidateRunId ||
    admissionIdentity.qualificationRunAttempt !== 1 ||
    admissionIdentity.sourceRevision !== expectedSourceRevision ||
    !SHA256.test(admissionIdentity.qualificationWorkflowSha256 ?? "")
  ) {
    return null;
  }

  return {
    repository: expectedRepository,
    workflowPath: QUALIFICATION_WORKFLOW_RELATIVE_PATH,
    workflowSha256: workflow.sha256,
    runId: candidateRunId,
    runAttempt: 1,
    workflowId: run.workflow_id,
    runApiReceiptSha256: runRecord.sha256,
    artifactApiReceiptSha256: artifactRecord.sha256,
    artifactId: artifact.id,
    artifactDigest: artifact.digest,
    artifactBytes: artifact.size_in_bytes,
  };
};
