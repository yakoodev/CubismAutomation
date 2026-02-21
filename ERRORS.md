# Cubism API Error Catalog

This document describes stable error codes returned by the Cubism agent API.

## Common Codes
- `method_not_allowed`: HTTP method is not supported for endpoint.
- `invalid_request`: Request body is malformed or required fields are missing.
- `bad_request`: Invalid command payload or unsupported command name.
- `forbidden`: Command denied by allow/deny policy.
- `unauthorized`: Missing/invalid auth token when auth mode is required.
- `auth_misconfigured`: Auth mode requires token but `CUBISM_AGENT_TOKEN` is not configured.
- `operation_failed`: Internal exception during endpoint execution.
- `unsupported_action`: Reflection method is not available in current Cubism runtime.
- `guardrail_violation`: Action blocked by runtime/document mode safety check.
- `no_effect`: Request accepted but post-verify state did not change as requested.
- `not_found`: Target object (mesh/deformer/parameter) is not found.
- `job_not_found`: Job id is unknown.
- `job_cannot_cancel`: Job is already terminal and cannot be canceled.
- `job_not_terminal`: Job cannot be deleted yet because it is still active.
- `invalid_action`: Unknown job action.
- `timeout_exceeded`: Job exceeded timeout budget.
- `recovered_interrupted`: Job was `queued/running` before restart and reconciled to `failed` on recovery.

## Runtime/Document Codes
- `no_document`: No active document in Cubism.
- `no_model_source`: Active document has no model source.
- `no_selected_mesh`: Target/active mesh is missing.

## Startup Codes
- `startup_timeout`: App controller was not ready before timeout.
- `startup_failed`: Generic startup automation failure.

## Parameter Codes
- `out_of_range`: Parameter value is outside `[min,max]`.
- `partial_failed`: Batch update has at least one failed item.

## Notes
- Responses may include additional `message` for diagnostics.
- New endpoints should reuse existing codes where possible.
