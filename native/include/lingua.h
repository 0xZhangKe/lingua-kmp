#ifndef LINGUA_H
#define LINGUA_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

/* All inputs are borrowed for the call only. Strings use UTF-16 code units and
 * explicit lengths, preserving embedded NUL and rejecting unpaired surrogates.
 * Nonzero lengths require valid, aligned pointers to that many initialized items.
 * Null pointers are permitted only for zero lengths.
 * Every operation returns an owned result, released exactly once with
 * lingua_result_free. Accessor pointers remain valid until that release.
 * Status: 0 = success, 1 = invalid argument, 2 = state/panic, 3 = allocation.
 * As with Rust's allocator, an unrecoverable OOM may abort the process.
 * Handles are owned by callers; destroy each successful create exactly once.
 * Calls retain in-flight detectors even if another thread destroys the handle.
 */
typedef struct { const uint16_t *data; uint64_t len; } LinguaText;
typedef struct LinguaResult LinguaResult;

LinguaResult *lingua_create(int32_t const *ids, uint64_t count, double distance, int32_t preload, int32_t low);
LinguaResult *lingua_destroy(int64_t handle);
LinguaResult *lingua_unload(int64_t handle);
LinguaResult *lingua_detect(int64_t handle, LinguaText text);
LinguaResult *lingua_detect_parallel(int64_t handle, LinguaText const *texts, uint64_t count);
LinguaResult *lingua_confidence(int64_t handle, LinguaText text, int32_t language);
LinguaResult *lingua_confidence_parallel(int64_t handle, LinguaText const *texts, uint64_t count, int32_t language);
LinguaResult *lingua_values(int64_t handle, LinguaText text);
LinguaResult *lingua_values_parallel(int64_t handle, LinguaText const *texts, uint64_t count);
LinguaResult *lingua_multiple(int64_t handle, LinguaText text);
LinguaResult *lingua_multiple_parallel(int64_t handle, LinguaText const *texts, uint64_t count);
LinguaResult *lingua_write_model(LinguaText input, LinguaText output, int32_t language, LinguaText pattern);
LinguaResult *lingua_write_test_data(LinguaText input, LinguaText output, LinguaText pattern, uint32_t count);
LinguaResult *lingua_write_unique(LinguaText output);
LinguaResult *lingua_write_most_common(LinguaText output, int32_t const *ids, uint64_t count, uint32_t amount);

/* Rows preserve batch input order. Single-result operations return one row.
 * Detect: language IDs (zero = unknown); confidence: doubles;
 * values: alternating language ID/confidence;
 * multiple: groups of [start UTF-16, end UTF-16, word count, language ID].
 * IDs and indices are i32 values represented exactly as doubles.
 * Successful create uses the separate int64 handle accessor, not a double.
 */
int32_t lingua_result_status(const LinguaResult *result);
const char *lingua_result_message(const LinguaResult *result);
int64_t lingua_result_handle(const LinguaResult *result);
uint64_t lingua_result_rows(const LinguaResult *result);
uint64_t lingua_result_row_len(const LinguaResult *result, uint64_t row);
const double *lingua_result_row_data(const LinguaResult *result, uint64_t row);
void lingua_result_free(LinguaResult *result);
#ifdef __cplusplus
}
#endif
#endif
