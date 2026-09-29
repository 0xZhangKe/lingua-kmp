//! JNI performs transport only. Every export uses the same panic/error boundary.
use crate::core::{self, BridgeError, Result};
use jni::{
    JNIEnv,
    objects::{JCharArray, JClass, JIntArray, JObject, JObjectArray, JString},
    sys::{jboolean, jdouble, jdoubleArray, jint, jintArray, jlong, jobjectArray},
};
use std::panic::{AssertUnwindSafe, catch_unwind};

impl From<jni::errors::Error> for BridgeError {
    fn from(error: jni::errors::Error) -> Self {
        Self::State(error.to_string())
    }
}

fn boundary<T: Default>(env: &mut JNIEnv, action: impl FnOnce(&mut JNIEnv) -> Result<T>) -> T {
    let outcome = catch_unwind(AssertUnwindSafe(|| action(env)));
    let error = match outcome {
        Ok(Ok(value)) => return value,
        Ok(Err(error)) => error,
        Err(_) => BridgeError::State("Rust language detector panicked".into()),
    };
    // Preserve JVM exceptions, notably OutOfMemoryError from JNI allocation.
    if !env.exception_check().unwrap_or(true) {
        let (class, message) = match error {
            BridgeError::Argument(message) => ("java/lang/IllegalArgumentException", message),
            BridgeError::State(message) => ("java/lang/IllegalStateException", message),
            BridgeError::Allocation(message) => ("java/lang/OutOfMemoryError", message),
        };
        let _ = env.throw_new(class, message);
    }
    T::default()
}

fn text(env: &mut JNIEnv, input: &JString) -> Result<String> {
    if input.is_null() {
        return Err(BridgeError::Argument("Null text".into()));
    }
    // Read UTF-16 explicitly: modified UTF-8 conversion can silently replace unpaired surrogates.
    let chars = env.call_method(input, "toCharArray", "()[C", &[])?.l()?;
    let chars = env.auto_local(JCharArray::from(chars));
    let mut units = vec![0; env.get_array_length(&*chars)? as usize];
    env.get_char_array_region(&*chars, 0, &mut units)?;
    String::from_utf16(&units)
        .map_err(|_| BridgeError::Argument("Text contains an unpaired UTF-16 surrogate".into()))
}

fn texts(env: &mut JNIEnv, inputs: &JObjectArray) -> Result<Vec<String>> {
    if inputs.is_null() {
        return Err(BridgeError::Argument("Null text array".into()));
    }
    let count = env.get_array_length(inputs)?;
    let mut values = Vec::new();
    for index in 0..count {
        let object = env.get_object_array_element(inputs, index)?;
        let input = env.auto_local(JString::from(object));
        values.push(text(env, &input)?);
    }
    Ok(values)
}

fn ints(env: &mut JNIEnv, values: &[i32]) -> Result<jintArray> {
    let count =
        i32::try_from(values.len()).map_err(|_| BridgeError::State("Result too large".into()))?;
    let array = env.new_int_array(count)?;
    env.set_int_array_region(&array, 0, values)?;
    Ok(array.into_raw())
}
fn doubles(env: &mut JNIEnv, values: &[f64]) -> Result<jdoubleArray> {
    let count =
        i32::try_from(values.len()).map_err(|_| BridgeError::State("Result too large".into()))?;
    let array = env.new_double_array(count)?;
    env.set_double_array_region(&array, 0, values)?;
    Ok(array.into_raw())
}
fn int_rows(env: &mut JNIEnv, rows: &[Vec<i32>]) -> Result<jobjectArray> {
    let array = env.new_object_array(rows.len() as i32, "[I", JObject::null())?;
    for (index, row) in rows.iter().enumerate() {
        let raw = ints(env, row)?;
        // SAFETY: raw is a fresh local JNI reference created above.
        let local = env.auto_local(unsafe { JObject::from_raw(raw) });
        env.set_object_array_element(&array, index as i32, &local)?;
    }
    Ok(array.into_raw())
}
fn double_rows(env: &mut JNIEnv, rows: &[Vec<f64>]) -> Result<jobjectArray> {
    let array = env.new_object_array(rows.len() as i32, "[D", JObject::null())?;
    for (index, row) in rows.iter().enumerate() {
        let raw = doubles(env, row)?;
        // SAFETY: raw is a fresh local JNI reference created above.
        let local = env.auto_local(unsafe { JObject::from_raw(raw) });
        env.set_object_array_element(&array, index as i32, &local)?;
    }
    Ok(array.into_raw())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_create(
    mut env: JNIEnv,
    _: JClass,
    ids: JIntArray,
    distance: jdouble,
    preload: jboolean,
    low: jboolean,
) -> jlong {
    boundary(&mut env, |env| {
        if ids.is_null() {
            return Err(BridgeError::Argument("Null language array".into()));
        }
        let mut values = vec![0; env.get_array_length(&ids)? as usize];
        env.get_int_array_region(&ids, 0, &mut values)?;
        core::create(&values, distance, preload != 0, low != 0)
    })
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_destroy(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
) {
    boundary(&mut env, |_| core::destroy(handle))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_unload(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
) {
    boundary(&mut env, |_| {
        core::get(handle)?.unload();
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_detect(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JString,
) -> jint {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = text(env, &input)?;
        Ok(detector.detect(&input))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_detectParallel(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JObjectArray,
) -> jintArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = texts(env, &input)?;
        ints(env, &detector.detect_parallel(&input))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_confidence(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JString,
    language: jint,
) -> jdouble {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = text(env, &input)?;
        detector.confidence(&input, language)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_confidenceParallel(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JObjectArray,
    language: jint,
) -> jdoubleArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = texts(env, &input)?;
        doubles(env, &detector.confidence_parallel(&input, language)?)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_values(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JString,
) -> jdoubleArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = text(env, &input)?;
        doubles(env, &detector.values(&input))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_valuesParallel(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JObjectArray,
) -> jobjectArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = texts(env, &input)?;
        double_rows(env, &detector.values_parallel(&input))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_multiple(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JString,
) -> jintArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = text(env, &input)?;
        ints(env, &detector.multiple(&input)?)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_multipleParallel(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    input: JObjectArray,
) -> jobjectArray {
    boundary(&mut env, |env| {
        let detector = core::get(handle)?;
        let input = texts(env, &input)?;
        int_rows(env, &detector.multiple_parallel(&input)?)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_writeModelFiles(
    mut env: JNIEnv,
    _: JClass,
    input: JString,
    output: JString,
    language: jint,
    char_class: JString,
) {
    boundary(&mut env, |env| {
        crate::writers::model(
            &text(env, &input)?,
            &text(env, &output)?,
            language,
            &text(env, &char_class)?,
        )
    })
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_writeTestDataFiles(
    mut env: JNIEnv,
    _: JClass,
    input: JString,
    output: JString,
    char_class: JString,
    count: jlong,
) {
    boundary(&mut env, |env| {
        crate::writers::test_data(
            &text(env, &input)?,
            &text(env, &output)?,
            &text(env, &char_class)?,
            count,
        )
    })
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_writeUniqueNgramFiles(
    mut env: JNIEnv,
    _: JClass,
    output: JString,
) {
    boundary(&mut env, |env| crate::writers::unique(&text(env, &output)?))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_writeMostCommonNgramFiles(
    mut env: JNIEnv,
    _: JClass,
    output: JString,
    ids: JIntArray,
    count: jlong,
) {
    boundary(&mut env, |env| {
        if ids.is_null() {
            return Err(BridgeError::Argument("Null language array".into()));
        }
        let mut values = vec![0; env.get_array_length(&ids)? as usize];
        env.get_int_array_region(&ids, 0, &mut values)?;
        crate::writers::most_common(&text(env, &output)?, &values, count)
    })
}
