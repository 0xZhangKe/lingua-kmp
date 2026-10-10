//! C transport for Kotlin/Native. Result memory is owned by Rust, never by Kotlin.
use crate::core::{self, BridgeError, Result};
use std::{
    ffi::CString,
    panic::{AssertUnwindSafe, catch_unwind},
};

#[repr(C)]
#[derive(Clone, Copy)]
pub struct LinguaText {
    pub data: *const u16,
    pub len: u64,
}

#[derive(Default)]
struct Payload {
    handle: i64,
    rows: Vec<Vec<f64>>,
}
impl Payload {
    fn row(row: Vec<f64>) -> Self {
        Self {
            handle: 0,
            rows: vec![row],
        }
    }
}

pub struct LinguaResult {
    status: i32,
    message: CString,
    payload: Payload,
}

fn boundary(action: impl FnOnce() -> Result<Payload>) -> *mut LinguaResult {
    let outcome = catch_unwind(AssertUnwindSafe(action));
    let (status, message, payload) = match outcome {
        Ok(Ok(payload)) => (0, String::new(), payload),
        error => {
            let (status, message) = match error {
                Ok(Err(BridgeError::Argument(message))) => (1, message),
                Ok(Err(BridgeError::State(message))) => (2, message),
                Ok(Err(BridgeError::Allocation(message))) => (3, message),
                Err(_) => (2, "Rust language detector panicked".into()),
                Ok(Ok(_)) => unreachable!(),
            };
            (status, message, Payload::default())
        }
    };
    Box::into_raw(Box::new(LinguaResult {
        status,
        message: CString::new(message.replace('\0', "\\0")).unwrap(),
        payload,
    }))
}

// SAFETY: the caller must provide readable aligned pointers for nonempty slices.
unsafe fn input_slice<'a, T>(data: *const T, len: u64) -> Result<&'a [T]> {
    let len = usize::try_from(len).map_err(|_| BridgeError::Argument("Input too large".into()))?;
    if len == 0 {
        return Ok(&[]);
    }
    if data.is_null() || !data.is_aligned() || len > isize::MAX as usize / std::mem::size_of::<T>()
    {
        return Err(BridgeError::Argument(
            "Invalid input pointer or length".into(),
        ));
    }
    Ok(unsafe { std::slice::from_raw_parts(data, len) })
}
unsafe fn decode_text(text: LinguaText) -> Result<String> {
    String::from_utf16(unsafe { input_slice(text.data, text.len)? })
        .map_err(|_| BridgeError::Argument("Text contains an unpaired UTF-16 surrogate".into()))
}
unsafe fn decode_texts(texts: *const LinguaText, count: u64) -> Result<Vec<String>> {
    unsafe { input_slice(texts, count)? }
        .iter()
        .map(|text| unsafe { decode_text(*text) })
        .collect()
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_create(
    ids: *const i32,
    count: u64,
    distance: f64,
    preload: i32,
    low: i32,
) -> *mut LinguaResult {
    boundary(|| {
        Ok(Payload {
            handle: core::create(
                unsafe { input_slice(ids, count)? },
                distance,
                preload != 0,
                low != 0,
            )?,
            rows: vec![],
        })
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_destroy(handle: i64) -> *mut LinguaResult {
    boundary(|| {
        core::destroy(handle)?;
        Ok(Payload::default())
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_unload(handle: i64) -> *mut LinguaResult {
    boundary(|| {
        core::get(handle)?.unload();
        Ok(Payload::default())
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_detect(handle: i64, text: LinguaText) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(vec![
            detector.detect(&unsafe { decode_text(text)? }) as f64,
        ]))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_detect_parallel(
    handle: i64,
    texts: *const LinguaText,
    count: u64,
) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(
            detector
                .detect_parallel(&unsafe { decode_texts(texts, count)? })
                .into_iter()
                .map(f64::from)
                .collect(),
        ))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_confidence(
    handle: i64,
    text: LinguaText,
    language: i32,
) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(vec![
            detector.confidence(&unsafe { decode_text(text)? }, language)?,
        ]))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_confidence_parallel(
    handle: i64,
    texts: *const LinguaText,
    count: u64,
    language: i32,
) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(detector.confidence_parallel(
            &unsafe { decode_texts(texts, count)? },
            language,
        )?))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_values(handle: i64, text: LinguaText) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(
            detector.values(&unsafe { decode_text(text)? }),
        ))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_values_parallel(
    handle: i64,
    texts: *const LinguaText,
    count: u64,
) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload {
            handle: 0,
            rows: detector.values_parallel(&unsafe { decode_texts(texts, count)? }),
        })
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_multiple(handle: i64, text: LinguaText) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload::row(
            detector
                .multiple(&unsafe { decode_text(text)? })?
                .into_iter()
                .map(f64::from)
                .collect(),
        ))
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_multiple_parallel(
    handle: i64,
    texts: *const LinguaText,
    count: u64,
) -> *mut LinguaResult {
    boundary(|| {
        let detector = core::get(handle)?;
        Ok(Payload {
            handle: 0,
            rows: detector
                .multiple_parallel(&unsafe { decode_texts(texts, count)? })?
                .into_iter()
                .map(|row| row.into_iter().map(f64::from).collect())
                .collect(),
        })
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_write_model(
    input: LinguaText,
    output: LinguaText,
    language: i32,
    pattern: LinguaText,
) -> *mut LinguaResult {
    boundary(|| {
        crate::writers::model(
            &unsafe { decode_text(input)? },
            &unsafe { decode_text(output)? },
            language,
            &unsafe { decode_text(pattern)? },
        )?;
        Ok(Payload::default())
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_write_test_data(
    input: LinguaText,
    output: LinguaText,
    pattern: LinguaText,
    count: u32,
) -> *mut LinguaResult {
    boundary(|| {
        crate::writers::test_data(
            &unsafe { decode_text(input)? },
            &unsafe { decode_text(output)? },
            &unsafe { decode_text(pattern)? },
            i64::from(count),
        )?;
        Ok(Payload::default())
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_write_unique(output: LinguaText) -> *mut LinguaResult {
    boundary(|| {
        crate::writers::unique(&unsafe { decode_text(output)? })?;
        Ok(Payload::default())
    })
}

// SAFETY: pointer arguments must follow the ownership and length contract in lingua.h.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_write_most_common(
    output: LinguaText,
    ids: *const i32,
    count: u64,
    amount: u32,
) -> *mut LinguaResult {
    boundary(|| {
        crate::writers::most_common(
            &unsafe { decode_text(output)? },
            unsafe { input_slice(ids, count)? },
            i64::from(amount),
        )?;
        Ok(Payload::default())
    })
}

// Accessors require a live result from this library; null is tolerated.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_status(result: *const LinguaResult) -> i32 {
    unsafe { result.as_ref() }.map_or(2, |r| r.status)
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_message(
    result: *const LinguaResult,
) -> *const std::ffi::c_char {
    unsafe { result.as_ref() }.map_or(std::ptr::null(), |r| r.message.as_ptr())
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_handle(result: *const LinguaResult) -> i64 {
    unsafe { result.as_ref() }.map_or(0, |r| r.payload.handle)
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_rows(result: *const LinguaResult) -> u64 {
    unsafe { result.as_ref() }.map_or(0, |r| r.payload.rows.len() as u64)
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_row_len(result: *const LinguaResult, row: u64) -> u64 {
    unsafe { result.as_ref() }
        .and_then(|r| r.payload.rows.get(usize::try_from(row).ok()?))
        .map_or(0, |v| v.len() as u64)
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_row_data(
    result: *const LinguaResult,
    row: u64,
) -> *const f64 {
    unsafe { result.as_ref() }
        .and_then(|r| r.payload.rows.get(usize::try_from(row).ok()?))
        .map_or(std::ptr::null(), |v| v.as_ptr())
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn lingua_result_free(result: *mut LinguaResult) {
    if !result.is_null() {
        drop(unsafe { Box::from_raw(result) });
    }
}

#[cfg(test)]
mod tests;
