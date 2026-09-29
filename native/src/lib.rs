use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use lingua::LanguageDetectorBuilder;
use std::ptr;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zhangke_lingua_LinguaNative_detect(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let text: String = match env.get_string(&input) {
        Ok(value) => value.into(),
        Err(_) => return ptr::null_mut(),
    };

    let detector = LanguageDetectorBuilder::from_all_languages()
        .build();

    let language = match detector.detect_language_of(&text) {
        Some(language) => language,
        None => return ptr::null_mut(),
    };

    let language_code = language
        .iso_code_639_1()
        .to_string()
        .to_lowercase();

    match env.new_string(language_code) {
        Ok(result) => result.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}
